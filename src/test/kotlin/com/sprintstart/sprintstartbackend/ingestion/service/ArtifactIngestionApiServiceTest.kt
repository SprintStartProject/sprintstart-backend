package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class ArtifactIngestionApiServiceTest {
    private val artifactRepository = mockk<ArtifactRepository>()
    private val service = ArtifactIngestionApiService(artifactRepository)

    private val artifactId = UUID.randomUUID()

    @Test
    fun `exists returns true when the repository has the artifact`() {
        every { artifactRepository.existsById(artifactId) } returns true

        assertTrue(service.exists(artifactId))
    }

    @Test
    fun `exists returns false when the repository does not have the artifact`() {
        every { artifactRepository.existsById(artifactId) } returns false

        assertFalse(service.exists(artifactId))
    }

    @Test
    fun `getHash returns null when the artifact does not exist`() {
        every { artifactRepository.findById(artifactId) } returns Optional.empty()

        assertNull(service.getHash(artifactId))
    }

    @Test
    fun `getHash returns the artifact's hash when it exists`() {
        val artifact = mockk<Artifact> {
            every { hash } returns "content-hash"
        }
        every { artifactRepository.findById(artifactId) } returns Optional.of(artifact)

        assertEquals("content-hash", service.getHash(artifactId))
    }
}
