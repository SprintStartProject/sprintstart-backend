package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.files.GithubFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class GithubArtifactFailedMapperTest {
    private val mapper = GithubArtifactFailedMapper()
    private val runId = UUID.randomUUID()

    @Test
    fun `toCommand maps failed commit with source url`() {
        val event = GithubCommitFetchFailedEvent(
            transactionId = runId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            sha = "abc123",
            reason = "Git failed",
        )

        val result = mapper.toCommand(event)

        assertThat(result.transactionId).isEqualTo(runId)
        assertThat(result.repositoryOwner).isEqualTo("owner")
        assertThat(result.repositoryName).isEqualTo("repo")
        assertThat(result.sourceId).isEqualTo("github:owner/repo:COMMIT:abc123")
        assertThat(result.sourceUrl).isEqualTo("https://github.com/owner/repo/commit/abc123")
        assertThat(result.artifactType).isEqualTo(ArtifactType.COMMIT)
        assertThat(result.reason).isEqualTo("Git failed")
    }

    @Test
    fun `toCommand maps failed file without source url`() {
        val event = GithubFileFetchFailedEvent(
            transactionId = runId,
            repositoryOwner = "owner",
            repositoryName = "repo",
            path = "src/main/App.kt",
            reason = "File missing",
        )

        val result = mapper.toCommand(event)

        assertThat(result.transactionId).isEqualTo(runId)
        assertThat(result.sourceId).isEqualTo("github:owner/repo:FILE:src/main/App.kt")
        assertThat(result.sourceUrl).isNull()
        assertThat(result.artifactType).isEqualTo(ArtifactType.FILE)
        assertThat(result.reason).isEqualTo("File missing")
    }
}
