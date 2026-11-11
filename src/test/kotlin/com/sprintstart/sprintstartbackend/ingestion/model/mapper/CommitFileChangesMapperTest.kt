package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.shared.git.CommitFile
import com.sprintstart.sprintstartbackend.shared.git.CommitFiles
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class CommitFileChangesMapperTest {
    private val mapper = CommitFileChangesMapper()
    private val committedAt = Instant.parse("2026-03-04T05:06:07Z")
    private val files = CommitFiles.Listed(listOf(CommitFile("src/A.kt", 3, 1, renamedFrom = "src/Old.kt")))

    private fun githubEvent(files: CommitFiles = this.files) = GithubCommitFetchedEvent(
        transactionId = UUID.randomUUID(),
        repositoryId = UUID.randomUUID(),
        repositoryOwner = "acme",
        repositoryName = "backend",
        author = "Ada Lovelace",
        authorEmail = "ada@example.com",
        date = committedAt,
        sha = "abc123",
        msg = "Fix it",
        files = files,
    )

    private fun bitbucketEvent(files: CommitFiles = this.files) = BitbucketCommitFetchedEvent(
        transactionId = UUID.randomUUID(),
        repositoryId = UUID.randomUUID(),
        workspace = "acme",
        slug = "backend",
        author = "Ada Lovelace",
        authorEmail = "ada@example.com",
        committedAt = committedAt,
        sha = "abc123",
        subject = "Fix it",
        sourceUrl = "https://bitbucket.org/acme/backend/commits/abc123",
        files = files,
    )

    @Test
    fun `maps a GitHub commit onto its files`() {
        val command = mapper.toCommand(githubEvent())

        assertThat(command.sourceId).isEqualTo("github:acme/backend:COMMIT:abc123")
        assertThat(command.repository).isEqualTo("github:acme/backend")
        assertThat(command.authorName).isEqualTo("Ada Lovelace")
        assertThat(command.authorEmail).isEqualTo("ada@example.com")
        assertThat(command.changedAt).isEqualTo(committedAt)
        assertThat(command.files).isEqualTo(files)
    }

    @Test
    fun `maps a Bitbucket commit onto its files`() {
        val command = mapper.toCommand(bitbucketEvent())

        assertThat(command.sourceId).isEqualTo("bitbucket:acme/backend:COMMIT:abc123")
        assertThat(command.repository).isEqualTo("bitbucket:acme/backend")
        assertThat(command.authorName).isEqualTo("Ada Lovelace")
        assertThat(command.authorEmail).isEqualTo("ada@example.com")
        assertThat(command.changedAt).isEqualTo(committedAt)
        assertThat(command.files).isEqualTo(files)
    }

    @Test
    fun `passes a commit with too many files through unchanged`() {
        val tooMany = CommitFiles.TooMany(500)

        assertThat(mapper.toCommand(githubEvent(tooMany)).files).isEqualTo(tooMany)
        assertThat(mapper.toCommand(bitbucketEvent(tooMany)).files).isEqualTo(tooMany)
    }

    @Test
    fun `attaches GitHub files to the artifact the artifact mapper stores for that commit`() {
        val event = githubEvent()

        assertThat(mapper.toCommand(event).sourceId).isEqualTo(GithubArtifactMapper().toCommand(event).sourceId)
    }

    @Test
    fun `attaches Bitbucket files to the artifact the artifact mapper stores for that commit`() {
        val event = bitbucketEvent()

        assertThat(mapper.toCommand(event).sourceId).isEqualTo(BitbucketArtifactMapper().toCommand(event).sourceId)
    }
}
