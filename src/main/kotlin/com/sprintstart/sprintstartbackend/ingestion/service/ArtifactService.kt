package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactContentResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.shared.ArtifactContentCodec
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Resolves project-scoped artifact content for authenticated callers.
 *
 * This service centralizes the authorization and ownership checks required before raw artifact
 * payloads can be returned to the API layer.
 */
@Service
class ArtifactService(
    private val userApi: UserApi,
    private val artifactRepository: ArtifactRepository,
) {
    /**
     * Loads one artifact payload when the authenticated user has access to the requested project.
     *
     * The method first verifies project access through the user module, then ensures the artifact
     * exists and is linked to the same project before returning its stored content and mime type.
     *
     * @param projectId The project that scopes access to the artifact.
     * @param artifactId The identifier of the artifact to retrieve.
     * @param authId The authenticated caller subject from the JWT.
     * @return The stored artifact payload together with the effective mime type.
     * @throws ResponseStatusException `403` when the caller has no access to the project.
     * @throws ResponseStatusException `404` when the artifact is missing, not linked to the
     * requested project, or has no stored content.
     */
    @Transactional(readOnly = true)
    fun getArtifactContent(projectId: UUID, artifactId: UUID, authId: String): ArtifactContentResponse {
        ensureAccessToProject(authId, projectId)
        val artifact = requireArtifact(artifactId)

        if (projectId !in artifact.projectIds) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "This artifact does not belong to project with id $projectId.",
            )
        }
        val storedContent = artifact.content
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact content not found")
        val mime = artifact.mime ?: MediaType.APPLICATION_OCTET_STREAM_VALUE

        val bodyBytes = if (artifact.sourceSystem == SourceSystem.UPLOAD) {
            ArtifactContentCodec.decode(storedContent, mime)
        } else {
            storedContent.toByteArray(Charsets.UTF_8)
        }

        return ArtifactContentResponse(
            content = bodyBytes,
            mime = mime,
        )
    }

    /**
     * Verifies project access through the user module before artifact content is exposed.
     *
     * @param authId The authenticated caller subject from the JWT.
     * @param projectId The project whose artifacts the caller wants to read.
     * @throws ResponseStatusException `403` when the caller has no access to the project.
     */
    fun ensureAccessToProject(authId: String, projectId: UUID) {
        val userHasAccessToProject = userApi.userHasAccessToProject(authId, projectId)
        if (!userHasAccessToProject) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "No access to project with id $projectId")
        }
    }

    /**
     * Loads an artifact or converts the missing-row case into the API-level not-found exception.
     *
     * @param artifactId The artifact id to resolve.
     * @return The persisted artifact entity.
     * @throws ResponseStatusException `404` when no artifact exists for the id.
     */
    fun requireArtifact(artifactId: UUID): Artifact {
        return artifactRepository
            .findById(artifactId)
            .orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Artifact not found")
            }
    }
}
