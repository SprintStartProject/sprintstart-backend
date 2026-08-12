package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.AssignedIssue
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.Optional
import java.util.UUID

class ArtifactIngestionApiServiceTest {
    private val artifactRepository = mockk<ArtifactRepository>()
    private val artifactMetadataJsonMapper = ArtifactMetadataJsonMapper(ObjectMapper())
    private val service = ArtifactIngestionApiService(
        artifactRepository,
        artifactMetadataJsonMapper,
        AssignedIssueReader(artifactMetadataJsonMapper),
    )

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
    fun `getOpenIssues returns only issues the tracker still reports as open`() {
        val open = issue(sourceId = "github:owner/repo:ISSUE:1", state = "OPEN")
        val closed = issue(sourceId = "github:owner/repo:ISSUE:2", state = "CLOSED")
        // Never captured, so nothing is known -- and an unknown state is not an open one.
        val unknown = issue(sourceId = "github:owner/repo:ISSUE:3", state = null)
        every {
            artifactRepository.findAllByProjectIdAndArtifactType(projectId, ArtifactType.ISSUE)
        } returns listOf(open, closed, unknown)

        val result = service.getOpenIssues(projectId)

        assertEquals(listOf("github:owner/repo:ISSUE:1"), result.map { it.sourceId })
    }

    /**
     * ⚠️ Mining skips an assigned issue because proposing somebody else's work is a wrong answer a
     * hire cannot detect. A person browsing is the opposite case: they can see who holds it and
     * decide anyway, and an issue simply missing from the list leaves them unable to tell "taken"
     * from "not ingested". So the marking is what this carries, never a filter.
     */
    @Test
    fun `getOpenIssues returns assigned issues too, marked`() {
        val assigned = issue(sourceId = "jira:ONB-1", state = "OPEN").apply { hasAssignee = true }
        val free = issue(sourceId = "jira:ONB-2", state = "OPEN").apply { hasAssignee = false }
        val unknown = issue(sourceId = "jira:ONB-3", state = "OPEN")
        every {
            artifactRepository.findAllByProjectIdAndArtifactType(projectId, ArtifactType.ISSUE)
        } returns listOf(assigned, free, unknown)

        val result = service.getOpenIssues(projectId).associate { it.sourceId to it.hasAssignee }

        assertEquals(mapOf("jira:ONB-1" to true, "jira:ONB-2" to false, "jira:ONB-3" to null), result)
    }

    @Test
    fun `getIssue returns the ingested issue with its state and assignee`() {
        val artifact = issue(sourceId = "github:owner/repo:ISSUE:7", state = "OPEN").apply { hasAssignee = true }
        every { artifactRepository.findBySourceId("github:owner/repo:ISSUE:7") } returns artifact

        val result = service.getIssue("github:owner/repo:ISSUE:7")

        assertEquals("OPEN", result?.state)
        assertEquals(true, result?.hasAssignee)
        assertEquals("GITHUB", result?.tracker)
        assertEquals(listOf("good first issue"), result?.labels)
    }

    /**
     * A pull request is not work to hand a newcomer, and accepting one here would put it in the
     * starter-work pool under an issue's name.
     */
    @Test
    fun `getIssue returns null for a source id that is not an issue`() {
        val pullRequest = issue(
            sourceId = "github:owner/repo:PULL_REQUEST:7",
            state = "OPEN",
            type = ArtifactType.PULL_REQUEST,
        )
        every { artifactRepository.findBySourceId("github:owner/repo:PULL_REQUEST:7") } returns pullRequest

        assertNull(service.getIssue("github:owner/repo:PULL_REQUEST:7"))
    }

    @Test
    fun `getIssue returns null when nothing with that source id is ingested`() {
        every { artifactRepository.findBySourceId("github:owner/repo:ISSUE:404") } returns null

        assertNull(service.getIssue("github:owner/repo:ISSUE:404"))
    }

    private fun issue(
        sourceId: String,
        state: String?,
        type: ArtifactType = ArtifactType.ISSUE,
    ) = Artifact(
        sourceSystem = SourceSystem.GITHUB,
        sourceId = sourceId,
        sourceUrl = "https://example.test/$sourceId",
        artifactType = type,
        title = "Fix the typo",
        content = "The README says teh.",
        mime = null,
        language = null,
        state = state,
        labels = mutableListOf("good first issue"),
        createdAtSource = null,
        updatedAtSource = Instant.parse("2026-08-01T10:00:00Z"),
        ingestionRun = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            status = IngestionRunStatus.COMPLETED,
        ),
        hash = null,
    )

    /**
     * ⚠️ A blank assignee name must match nobody, and the guard belongs here rather than in the
     * reader: a Jira account whose display name is blank is not impossible, and crediting one hire
     * with every such issue in a project is the loudest possible version of a wrong answer. It also
     * saves loading a project's whole issue set to filter it all away.
     */
    @Test
    fun `getAssignedIssues matches nobody for a blank name and reads nothing`() {
        assertEquals(emptyList<AssignedIssue>(), service.getAssignedIssues(UUID.randomUUID(), "   "))

        verify(exactly = 0) {
            artifactRepository.findAllByProjectIdAndSourceSystemAndArtifactType(any(), any(), any())
        }
    }
}
