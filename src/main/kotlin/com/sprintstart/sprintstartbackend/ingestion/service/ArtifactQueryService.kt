package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactFilterCriteria
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactFacetsResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactPageResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.PageMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
    @Transactional(readOnly = true)
    @Tracked("Retrieving list of all artifacts")
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
     * Returns one paginated artifact list limited to a single project visible to the caller using criteria.
     *
     * @param page The 1-based page number to return.
     * @param size The maximum number of artifacts to include in one page.
     * @param criteria Filter criteria containing search string, types, sources, repos, and formats.
     * @param projectId The SprintStart project that scopes the artifact listing.
     * @param authId The authenticated caller subject from the JWT.
     * @return One project-scoped artifact page together with pagination metadata.
     * @throws ResponseStatusException `403` when the caller has no access to the project.
     * @throws IllegalArgumentException when Spring Data rejects the requested page or page size.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving list of artifacts for project")
    fun getProjectArtifacts(
        page: Int,
        size: Int,
        criteria: ArtifactFilterCriteria,
        projectId: UUID,
        authId: String,
    ): ArtifactPageResponse {
        ensureAccessToProject(authId, projectId)
        val pageable = PageRequest.of(
            page - 1,
            size,
            Sort.by("ingestedAt").descending().and(Sort.by("id").ascending()),
        )

        val result: Page<ArtifactResponse> =
            artifactRepository.findProjectArtifactsWithCriteria(projectId, criteria, pageable)
        return ArtifactPageResponse(
            items = result.content,
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
     * Legacy overload for project artifact queries specifying only a filter string.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving list of artifacts for project")
    fun getProjectArtifacts(
        page: Int,
        size: Int,
        filter: String?,
        projectId: UUID,
        authId: String,
    ): ArtifactPageResponse = getProjectArtifacts(
        page = page,
        size = size,
        criteria = ArtifactFilterCriteria(search = filter),
        projectId = projectId,
        authId = authId,
    )

    /**
     * Returns aggregated facet counts for a project based on the supplied criteria.
     *
     * @param projectId The SprintStart project that scopes the artifact listing.
     * @param criteria Active filter criteria.
     * @param authId The authenticated caller subject from the JWT.
     * @return Aggregated facet counts.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving artifact facets for project")
    fun getProjectArtifactFacets(
        projectId: UUID,
        criteria: ArtifactFilterCriteria,
        authId: String,
    ): ArtifactFacetsResponse {
        ensureAccessToProject(authId, projectId)
        return artifactRepository.findFacets(projectId, criteria)
    }

    /**
     * Retrieves a single artifact by its ID within the project scope.
     *
     * @param projectId The SprintStart project that scopes the artifact.
     * @param artifactId The ID of the artifact to retrieve.
     * @param authId The authenticated caller subject from the JWT.
     * @return The artifact response DTO.
     * @throws ResponseStatusException `403` if access is denied, `404` if not found in project.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving single artifact for project")
    fun getArtifact(
        projectId: UUID,
        artifactId: UUID,
        authId: String,
    ): ArtifactResponse {
        ensureAccessToProject(authId, projectId)
        val artifact = artifactRepository.findByIdAndProjectId(artifactId, projectId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Artifact $artifactId not found in project $projectId",
            )
        return artifactMapper.toResponse(artifact)
    }

    /**
     * Verifies that the authenticated caller may read artifacts for the requested project.
     *
     * @param authId The authenticated caller subject from the JWT.
     * @param projectId The SprintStart project whose access is being checked.
     * @throws ResponseStatusException `403` when the caller has no access to the project.
     */
    private fun ensureAccessToProject(authId: String, projectId: UUID) {
        val userHasAccessToProject = userApi.userHasAccessToProject(authId, projectId)
        if (!userHasAccessToProject) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "No access to project with id $projectId")
        }
    }
}
