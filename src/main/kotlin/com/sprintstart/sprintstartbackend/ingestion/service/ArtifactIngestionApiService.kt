package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.ArtifactDto
import com.sprintstart.sprintstartbackend.ingestion.external.model.ArtifactSourceScope
import com.sprintstart.sprintstartbackend.ingestion.external.model.toDto
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Service implementation of the ingestion API used by other modules.
 *
 * A small adapter over the artifact repository. It exposes read operations and source reuse
 * linking without exposing internal ingestion entities or repositories to other modules.
 */
@Service
internal class ArtifactIngestionApiService(
    private val artifactRepository: ArtifactRepository,
) : ArtifactIngestionApi {
    @Transactional(readOnly = true)
    @Tracked("Retrieving first ingestion time of component")
    override fun getFirstIngestedAt(component: String): Instant? {
        return artifactRepository.findFirstIngestedAt(component)
    }

    @Transactional(readOnly = true)
    @Tracked("Retrieving first ingestion time of list of components")
    override fun getFirstIngestedAt(components: Collection<String>): Map<String, Instant> {
        return components
            .distinct()
            .mapNotNull { component ->
                artifactRepository.findFirstIngestedAt(component)?.let { component to it }
            }.toMap()
    }

    @Transactional(readOnly = true)
    @Tracked("Checking if artifact exists")
    override fun exists(artifactId: UUID): Boolean {
        return artifactRepository.existsById(artifactId)
    }

    @Transactional(readOnly = true)
    @Tracked("Checking if artifact exists in project")
    override fun existsInProject(projectId: UUID, artifactId: UUID): Boolean {
        return artifactRepository.findById(artifactId).map { it.projectIds.contains(projectId) }.orElse(false)
    }

    @Transactional
    @Tracked("Linking existing source artifacts to project")
    override fun linkExistingSourceArtifacts(sourceScope: ArtifactSourceScope, projectId: UUID): Int {
        val sourceIdPrefix = sourceScope.sourceIdPrefix?.takeIf { it.isNotBlank() }
        val sourceUrlPrefix = sourceScope.sourceUrlPrefix?.takeIf { it.isNotBlank() }
        require(sourceIdPrefix != null || sourceUrlPrefix != null) {
            "Artifact source scope must include a source id prefix or source URL prefix"
        }

        val artifacts = artifactRepository.findAllBySourceScope(
            sourceSystem = sourceScope.sourceSystem,
            sourceIdPrefix = sourceIdPrefix,
            sourceUrlPrefix = sourceUrlPrefix,
        )
        artifacts.forEach { artifact ->
            artifact.addProjectId(projectId)
        }
        return artifacts.size
    }

    @Transactional(readOnly = true)
    @Tracked("Retrieving hash of artifact")
    override fun getHash(artifactId: UUID): String? {
        return artifactRepository.findById(artifactId).orElse(null)?.hash
    }

    @Transactional(readOnly = true)
    @Tracked("Retrieving artifact by id")
    override fun findArtifactById(artifactId: UUID): ArtifactDto? {
        return artifactRepository.findById(artifactId).map { it.toDto() }.orElse(null)
    }
}
