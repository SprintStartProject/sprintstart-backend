package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.github.external.events.commits.GithubCommitFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubArtifactFailedMapperTest {
    private val mapper = GithubArtifactFailedMapper()
    private val runId = UUID.randomUUID()
    private val repositoryId = UUID.randomUUID()

    @Test
    fun `toCommand maps failed commit with source url`() {
        val event = GithubCommitFetchFailedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            sha = "abc123",
            reason = "Git failed",
        )

        val result = mapper.toCommand(event)
        val metadata = result.metadata as GithubArtifactMetadata

        assertThat(result.transactionId).isEqualTo(runId)
        assertThat(result.sourceId).isEqualTo("github:owner/repo:COMMIT:abc123")
        assertThat(result.sourceUrl).isEqualTo("https://github.com/owner/repo/commit/abc123")
        assertThat(result.artifactType).isEqualTo(ArtifactType.COMMIT)
        assertThat(result.reason).isEqualTo("Git failed")
        assertThat(metadata.repositoryId).isEqualTo(repositoryId)
        assertThat(metadata.repositoryFullName).isEqualTo("owner/repo")
    }

    @Test
    fun `toCommand maps failed file without source url`() {
        val event = GithubFileFetchFailedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "src/main/App.kt",
            reason = "File missing",
        )

        val result = mapper.toCommand(event)
        val metadata = result.metadata as GithubArtifactMetadata

        assertThat(result.transactionId).isEqualTo(runId)
        assertThat(result.sourceId).isEqualTo("github:owner/repo:FILE:src/main/App.kt")
        assertThat(result.sourceUrl).isNull()
        assertThat(result.artifactType).isEqualTo(ArtifactType.FILE)
        assertThat(result.reason).isEqualTo("File missing")
        assertThat(metadata.repositoryId).isEqualTo(repositoryId)
        assertThat(metadata.repositoryFullName).isEqualTo("owner/repo")
    }
}
