package com.sprintstart.sprintstartbackend.ingestion.model.mapper.ingestion

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * The project ids carried here are what makes an artifact retrievable at all: the AI service
 * stores them on every chunk and its retrieval is fail-closed on them. An artifact synced without
 * them is invisible to *every* project rather than visible to all of them, so a silent drop in
 * this mapper would look like an empty knowledge base rather than an error.
 */
class ArtifactAiMapperTest {
    private val mapper = ArtifactAiMapper()

    private fun artifact(projectIds: Set<UUID>) = Artifact(
        id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "github:owner/repo:FILE:README.md",
        sourceUrl = "https://github.com/owner/repo/blob/main/README.md",
        artifactType = ArtifactType.FILE,
        title = "README.md",
        content = "content",
        mime = "text/markdown",
        language = "Markdown",
        createdAtSource = null,
        updatedAtSource = Instant.parse("2026-06-19T09:15:30Z"),
        ingestionRun = IngestionRun(
            id = UUID.randomUUID(),
            sourceSystem = SourceSystem.GITHUB,
            status = IngestionRunStatus.COMPLETED,
        ),
        hash = "hash",
    ).apply { addProjectIds(projectIds) }

    @Test
    fun `carries every project membership of the artifact`() {
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()

        val request = mapper.toIngestRequest(artifact(setOf(first, second)))

        assertThat(request.projectIds)
            .containsExactlyInAnyOrder(first.toString(), second.toString())
    }

    @Test
    fun `sends an empty list for an artifact belonging to no project`() {
        val request = mapper.toIngestRequest(artifact(emptySet()))

        // Not an error at this layer — the artifact genuinely has no membership yet. It simply
        // stays unretrievable until a project assignment exists and it is re-synced.
        assertThat(request.projectIds).isEmpty()
    }

    @Test
    fun `maps the remaining artifact fields unchanged`() {
        val projectId = UUID.randomUUID()

        val request = mapper.toIngestRequest(artifact(setOf(projectId)))

        assertThat(request.artifactId).isEqualTo("3fa85f64-5717-4562-b3fc-2c963f66afa6")
        assertThat(request.sourceSystem).isEqualTo(SourceSystem.GITHUB)
        assertThat(request.sourceId).isEqualTo("github:owner/repo:FILE:README.md")
        assertThat(request.sourceUrl).isEqualTo("https://github.com/owner/repo/blob/main/README.md")
        assertThat(request.artifactType).isEqualTo(ArtifactType.FILE)
        assertThat(request.title).isEqualTo("README.md")
        assertThat(request.bodyText).isEqualTo("content")
        assertThat(request.mime).isEqualTo("text/markdown")
        assertThat(request.language).isEqualTo("Markdown")
    }
}
