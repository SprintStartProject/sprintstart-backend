package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import com.sprintstart.sprintstartbackend.upload.repository.UploadedArtifactRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class UploadApiServiceTest {
    private val uploadedArtifactRepository = mockk<UploadedArtifactRepository>()
    private val service = UploadApiService(uploadedArtifactRepository)

    private val artifactId = UUID.randomUUID()

    @Test
    fun `getHash returns null when no uploaded artifact exists`() {
        every { uploadedArtifactRepository.findById(artifactId) } returns Optional.empty()

        assertNull(service.getHash(artifactId))
    }

    @Test
    fun `getHash returns the uploaded artifact's hash when it exists`() {
        val artifact = mockk<UploadedArtifact> {
            every { hash } returns "content-hash"
        }
        every { uploadedArtifactRepository.findById(artifactId) } returns Optional.of(artifact)

        assertEquals("content-hash", service.getHash(artifactId))
    }
}
