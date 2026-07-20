package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.ArtifactDto
import com.sprintstart.sprintstartbackend.ingestion.external.model.toDto
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Service implementation of the ingestion metadata API used by other modules.
 *
 * A small read-only adapter over the artifact repository; it does not touch the ingestion write
 * path or expose internal ingestion entities.
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
