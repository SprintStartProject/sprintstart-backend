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

class ArtifactAiMapperTest {
    private val mapper = ArtifactAiMapper()

    private fun ingestionRun() = IngestionRun(
        id = UUID.randomUUID(),
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.RUNNING,
    )

    private fun artifact(projectIds: Set<UUID>) = Artifact(
        id = UUID.fromString("3fa85f64-5717-4562-b3fc-2c963f66afa6"),
        sourceSystem = SourceSystem.GITHUB,
        sourceId = "github:owner/repo:FILE:README.md",
        sourceUrl = "https://github.com/owner/repo/blob/main/README.md",
        sourceVersion = "v1",
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
    fun `toIngestRequest forwards issue state and labels`() {
        val artifact = Artifact(
            sourceSystem = SourceSystem.GITHUB,
            sourceId = "github:owner/repo:ISSUE:42",
            sourceUrl = "https://github.com/owner/repo/issues/42",
            artifactType = ArtifactType.ISSUE,
            title = "Issue #42 Bug report",
            content = "Something broke",
            mime = null,
            language = null,
            state = "OPEN",
            labels = mutableListOf("bug", "good first issue"),
            ingestionRun = ingestionRun(),
            hash = "hash",
            createdAtSource = null,
            updatedAtSource = null,
        )

        val result = mapper.toIngestRequest(artifact)

        assertThat(result.state).isEqualTo("OPEN")
        assertThat(result.labels).containsExactly("bug", "good first issue")
    }

    @Test
    fun `toIngestRequest copies labels out instead of referencing the entity's live collection`() {
        // Regression test: a real Hibernate-managed Artifact.labels is a lazy PersistentBag, not
        // a plain List. Handing that reference straight to the DTO throws
        // LazyInitializationException whenever it's serialized after the session that loaded it
        // has closed (confirmed in a real ingestion run). Mutating the source after mapping and
        // asserting the DTO is unaffected proves toIngestRequest copies rather than references.
        val sourceLabels = mutableListOf("bug")
        val artifact = Artifact(
            sourceSystem = SourceSystem.GITHUB,
            sourceId = "github:owner/repo:ISSUE:43",
            sourceUrl = "https://github.com/owner/repo/issues/43",
            artifactType = ArtifactType.ISSUE,
            title = "Issue #43",
            content = "Body",
            mime = null,
            language = null,
            state = "OPEN",
            labels = sourceLabels,
            ingestionRun = ingestionRun(),
            hash = "hash",
            createdAtSource = null,
            updatedAtSource = null,
        )

        val result = mapper.toIngestRequest(artifact)
        sourceLabels.add("added after mapping")

        assertThat(result.labels).containsExactly("bug")
    }

    @Test
    fun `toIngestRequest defaults to null state and empty labels for a non-issue artifact`() {
        val artifact = Artifact(
            sourceSystem = SourceSystem.GITHUB,
            sourceId = "github:owner/repo:FILE:src/main/App.kt",
            sourceUrl = "https://github.com/owner/repo/blob/main/src/main/App.kt",
            artifactType = ArtifactType.FILE,
            title = "App.kt",
            content = "fun main() = Unit",
            mime = "text/x-kotlin",
            language = "Kotlin",
            ingestionRun = ingestionRun(),
            hash = "hash",
            createdAtSource = null,
            updatedAtSource = null,
        )

        val result = mapper.toIngestRequest(artifact)

        assertThat(result.state).isNull()
        assertThat(result.labels).isEmpty()
    }

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
