package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.ArtifactSourceScope
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
    private val projectId = UUID.randomUUID()

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

    @Test
    fun `linkExistingSourceArtifacts adds project id to matching artifacts`() {
        val existingProjectId = UUID.randomUUID()
        val artifact = artifact(projectIds = mutableSetOf(existingProjectId))
        val sourceScope = ArtifactSourceScope(
            sourceSystem = SourceSystem.GITHUB,
            sourceIdPrefix = "github:owner/repo:",
        )
        every {
            artifactRepository.findAllBySourceScope(SourceSystem.GITHUB, "github:owner/repo:", null)
        } returns listOf(artifact)

        val linkedCount = service.linkExistingSourceArtifacts(sourceScope, projectId)

        assertEquals(1, linkedCount)
        assertEquals(setOf(existingProjectId, projectId), artifact.projectIds)
        verify(exactly = 0) {
            artifactRepository.save(any())
        }
    }

    @Test
    fun `linkExistingSourceArtifacts is idempotent when project is already linked`() {
        val artifact = artifact(projectIds = mutableSetOf(projectId))
        val sourceScope = ArtifactSourceScope(
            sourceSystem = SourceSystem.GITHUB,
            sourceIdPrefix = "github:owner/repo:",
        )
        every {
            artifactRepository.findAllBySourceScope(SourceSystem.GITHUB, "github:owner/repo:", null)
        } returns listOf(artifact)

        service.linkExistingSourceArtifacts(sourceScope, projectId)
        service.linkExistingSourceArtifacts(sourceScope, projectId)

        assertEquals(setOf(projectId), artifact.projectIds)
        verify(exactly = 0) {
            artifactRepository.save(any())
        }
    }

    @Test
    fun `linkExistingSourceArtifacts can match artifacts by source url prefix`() {
        val artifact = artifact(
            sourceSystem = SourceSystem.JIRA,
            sourceId = "10001",
            sourceUrl = "https://jira.example.com/browse/SPR-1",
        )
        val sourceScope = ArtifactSourceScope(
            sourceSystem = SourceSystem.JIRA,
            sourceUrlPrefix = "https://jira.example.com/browse/",
        )
        every {
            artifactRepository.findAllBySourceScope(
                SourceSystem.JIRA,
                null,
                "https://jira.example.com/browse/",
            )
        } returns listOf(artifact)

        val linkedCount = service.linkExistingSourceArtifacts(sourceScope, projectId)

        assertEquals(1, linkedCount)
        assertEquals(setOf(projectId), artifact.projectIds)
    }

    @Test
    fun `linkExistingSourceArtifacts handles empty source matches without creating artifacts`() {
        val sourceScope = ArtifactSourceScope(
            sourceSystem = SourceSystem.GITHUB,
            sourceIdPrefix = "github:missing/repo:",
        )
        every {
            artifactRepository.findAllBySourceScope(SourceSystem.GITHUB, "github:missing/repo:", null)
        } returns emptyList()

        val linkedCount = service.linkExistingSourceArtifacts(sourceScope, projectId)

        assertEquals(0, linkedCount)
        verify(exactly = 0) {
            artifactRepository.save(any())
        }
    }

    private fun artifact(
        sourceSystem: SourceSystem = SourceSystem.GITHUB,
        sourceId: String = "github:owner/repo:FILE:src/main/App.kt",
        sourceUrl: String? = "https://github.com/owner/repo/blob/main/src/main/App.kt",
        projectIds: MutableSet<UUID> = mutableSetOf(),
    ) = Artifact(
        sourceSystem = sourceSystem,
        sourceId = sourceId,
        sourceUrl = sourceUrl,
        artifactType = ArtifactType.FILE,
        title = "App.kt",
        content = "content",
        mime = "text/x-kotlin",
        language = "Kotlin",
        projectIdsInternal = projectIds,
        createdAtSource = null,
        updatedAtSource = null,
        ingestionRun = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = sourceSystem,
            status = IngestionRunStatus.COMPLETED,
        ),
        hash = "hash",
    )
}
