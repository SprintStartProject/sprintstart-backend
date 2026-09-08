package com.sprintstart.sprintstartbackend.ingestion.model.mapper.ingestion

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class ArtifactAiMapperTest {
    private val mapper = ArtifactAiMapper()

    private fun ingestionRun() = IngestionRun(
        id = UUID.randomUUID(),
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.RUNNING,
    )

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
}
