package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactPageResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.PageMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Resolves paginated artifact listings for administrative and project-scoped reads.
 *
 * This service centralizes access validation, filtering, and pagination before mapping persisted
 * artifacts into API response models.
 */
@Service
class ArtifactQueryService(
    private val artifactRepository: ArtifactRepository,
    private val artifactMapper: ArtifactMapper,
    private val userApi: UserApi,
) {
    /**
     * Returns one paginated artifact list across all projects.
     *
     * When a filter is provided, the search is applied case-insensitively across the configured
     * searchable artifact fields.
     *
     * @param page The 1-based page number to return.
     * @param size The maximum number of artifacts to include in one page.
     * @param filter Optional case-insensitive text used to narrow the result set.
     * @return One artifact page together with pagination metadata.
     * @throws IllegalArgumentException when Spring Data rejects the requested page or page size.
     */
    fun getAllArtifacts(page: Int, size: Int, filter: String?): ArtifactPageResponse {
        val pageable = PageRequest.of(
            page - 1,
            size,
            Sort.by("ingestedAt").descending(),
        )

        val result: Page<Artifact> =
            if (filter.isNullOrBlank()) {
                artifactRepository.findAll(pageable)
            } else {
                artifactRepository.search(filter.trim(), pageable)
            }
        return ArtifactPageResponse(
            items = result.content.map { artifactMapper.toResponse(it) },
            page = PageMetadata(
                number = page.toLong(),
                size = size.toLong(),
                totalElements = result.totalElements,
                totalPages = result.totalPages.toLong(),
                hasNext = result.hasNext(),
                hasPrevious = result.hasPrevious(),
            ),
        )
    }

    /**
     * Returns one paginated artifact list limited to a single project visible to the caller.
     *
     * The method first validates project access and then delegates to project-scoped repository
     * queries using the same filter semantics as the global artifact search.
     *
     * @param page The 1-based page number to return.
     * @param size The maximum number of artifacts to include in one page.
     * @param filter Optional case-insensitive text used to narrow the result set.
     * @param projectId The SprintStart project that scopes the artifact listing.
     * @param authId The authenticated caller subject from the JWT.
     * @return One project-scoped artifact page together with pagination metadata.
     * @throws ResponseStatusException `403` when the caller has no access to the project.
     * @throws IllegalArgumentException when Spring Data rejects the requested page or page size.
     */
    fun getProjectArtifacts(
        page: Int,
        size: Int,
        filter: String?,
        projectId: UUID,
        authId: String,
    ): ArtifactPageResponse {
        ensureAccessToProject(authId, projectId)
        val pageable = PageRequest.of(
            page - 1,
            size,
            Sort.by("ingestedAt").descending(),
        )

        val result: Page<Artifact> =
            if (filter.isNullOrBlank()) {
                artifactRepository.findAllByProjectId(projectId, pageable)
            } else {
                artifactRepository.searchByProjectId(projectId, filter.trim(), pageable)
            }
        return ArtifactPageResponse(
            items = result.content.map { artifactMapper.toResponse(it) },
            page = PageMetadata(
                number = page.toLong(),
                size = size.toLong(),
                totalElements = result.totalElements,
                totalPages = result.totalPages.toLong(),
                hasNext = result.hasNext(),
                hasPrevious = result.hasPrevious(),
            ),
        )
    }

    /**
     * Verifies that the authenticated caller may read artifacts for the requested project.
     *
     * @param authId The authenticated caller subject from the JWT.
     * @param projectId The SprintStart project whose access is being checked.
     * @throws ResponseStatusException `403` when the caller has no access to the project.
     */
    fun ensureAccessToProject(authId: String, projectId: UUID) {
        val userHasAccessToProject = userApi.userHasAccessToProject(authId, projectId)
        if (!userHasAccessToProject) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "No access to project with id $projectId")
        }
    }
}
