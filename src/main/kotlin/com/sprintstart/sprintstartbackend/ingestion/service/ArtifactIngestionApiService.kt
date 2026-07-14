package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

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
    override fun getFirstIngestedAt(component: String): Instant? {
        return artifactRepository.findFirstIngestedAt(component)
    }

    @Transactional(readOnly = true)
    override fun getFirstIngestedAt(components: Collection<String>): Map<String, Instant> {
        return components
            .distinct()
            .mapNotNull { component ->
                artifactRepository.findFirstIngestedAt(component)?.let { component to it }
            }.toMap()
    }
}
