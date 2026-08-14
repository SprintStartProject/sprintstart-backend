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
    private val sourceIdString = artifactId.toString()

    // ---------- exists() ----------

    @Test
    fun `exists returns true when the repository has the artifact`() {
        every { artifactRepository.existsById(artifactId) } returns true

        assertTrue(service.exists(artifactId))
    }

    @Test
    fun `exists returns true when the artifact is found by sourceId as fallback`() {
        every { artifactRepository.existsById(artifactId) } returns false
        every { artifactRepository.findBySourceId(sourceIdString) } returns mockk()

        assertTrue(service.exists(artifactId))
    }

    @Test
    fun `exists returns false when neither id nor sourceId match`() {
        every { artifactRepository.existsById(artifactId) } returns false
        every { artifactRepository.findBySourceId(sourceIdString) } returns null

        assertFalse(service.exists(artifactId))
    }

    // ---------- findArtifactById() ----------

    @Test
    fun `findArtifactById returns the DTO when artifact is found by id`() {
        val artifact = mockk<Artifact>(relaxed = true) {
            every { id } returns artifactId
            every { hash } returns "content-hash"
        }
        every { artifactRepository.findById(artifactId) } returns Optional.of(artifact)

        val result = service.findArtifactById(artifactId)

        assertEquals(artifactId, result?.id)
    }

    @Test
    fun `findArtifactById falls back to sourceId lookup when id not found`() {
        val artifact = mockk<Artifact>(relaxed = true) {
            every { id } returns artifactId
        }
        every { artifactRepository.findById(artifactId) } returns Optional.empty()
        every { artifactRepository.findBySourceId(sourceIdString) } returns artifact

        val result = service.findArtifactById(artifactId)

        assertEquals(artifactId, result?.id)
    }

    @Test
    fun `findArtifactById returns null when neither id nor sourceId match`() {
        every { artifactRepository.findById(artifactId) } returns Optional.empty()
        every { artifactRepository.findBySourceId(sourceIdString) } returns null

        assertNull(service.findArtifactById(artifactId))
    }

    // ---------- getHash() ----------

    @Test
    fun `getHash returns null when the artifact does not exist`() {
        every { artifactRepository.findById(artifactId) } returns Optional.empty()
        every { artifactRepository.findBySourceId(sourceIdString) } returns null

        assertNull(service.getHash(artifactId))
    }

    @Test
    fun `getHash returns the artifact's hash when it exists`() {
        val artifact = mockk<Artifact>(relaxed = true) {
            every { hash } returns "content-hash"
        }
        every { artifactRepository.findById(artifactId) } returns Optional.of(artifact)

        assertEquals("content-hash", service.getHash(artifactId))
    }

    @Test
    fun `getHash resolves via sourceId fallback and returns the hash`() {
        val artifact = mockk<Artifact>(relaxed = true) {
            every { hash } returns "source-hash"
        }
        every { artifactRepository.findById(artifactId) } returns Optional.empty()
        every { artifactRepository.findBySourceId(sourceIdString) } returns artifact

        assertEquals("source-hash", service.getHash(artifactId))
    }

    @Test
    fun `getHash returns null when sourceId fallback also fails`() {
        every { artifactRepository.findById(artifactId) } returns Optional.empty()
        every { artifactRepository.findBySourceId(sourceIdString) } returns null

        assertNull(service.getHash(artifactId))
    }

    // ---------- existsInProject() ----------

    @Test
    fun `existsInProject returns true when found by sourceId and project matches`() {
        val projectId = UUID.randomUUID()
        val artifact = mockk<Artifact>(relaxed = true) {
            every { projectIds } returns setOf(projectId)
        }
        every { artifactRepository.findById(artifactId) } returns Optional.empty()
        every { artifactRepository.findBySourceId(sourceIdString) } returns artifact

        assertTrue(service.existsInProject(projectId, artifactId))
    }

    @Test
    fun `existsInProject returns false when found by sourceId but project does not match`() {
        val differentProjectId = UUID.randomUUID()
        val artifact = mockk<Artifact>(relaxed = true) {
            every { projectIds } returns setOf(UUID.randomUUID())
        }
        every { artifactRepository.findById(artifactId) } returns Optional.empty()
        every { artifactRepository.findBySourceId(sourceIdString) } returns artifact

        assertFalse(service.existsInProject(differentProjectId, artifactId))
    }

    @Test
    fun `existsInProject returns false when artifact not found`() {
        every { artifactRepository.findById(artifactId) } returns Optional.empty()
        every { artifactRepository.findBySourceId(sourceIdString) } returns null

        assertFalse(service.existsInProject(UUID.randomUUID(), artifactId))
    }
}
