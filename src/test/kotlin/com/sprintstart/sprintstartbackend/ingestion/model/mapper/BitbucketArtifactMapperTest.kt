package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.PrComment
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.PrParticipant
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataMember
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketWorkspaceMetadataArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.util.sha256
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class BitbucketArtifactMapperTest {
    private val mapper = BitbucketArtifactMapper()
    private val runId = UUID.randomUUID()
    private val repositoryId = UUID.randomUUID()

    @Test
    fun `toCommand maps workspace metadata into a project-less org metadata command`() {
        val event = BitbucketWorkspaceMetadataFetchedEvent(
            transactionId = runId,
            workspace = "sprintstart",
            uuid = "{ws-uuid}",
            name = "SprintStart",
            isPrivate = true,
            createdOn = "2024-01-01T00:00:00Z",
            url = "https://bitbucket.org/sprintstart",
            members = listOf(
                BitbucketWorkspaceMetadataMember(accountId = "{account-1}", nickname = "alice", displayName = "Alice"),
            ),
        )

        val result = mapper.toCommand(event)

        assertThat(result.ingestionRunId).isEqualTo(runId)
        assertThat(result.sourceSystem).isEqualTo(SourceSystem.BITBUCKET)
        assertThat(result.sourceId).isEqualTo("bitbucket:sprintstart:ORG_METADATA")
        assertThat(result.sourceUrl).isEqualTo("https://bitbucket.org/sprintstart")
        assertThat(result.artifactType).isEqualTo(ArtifactType.ORG_METADATA)
        assertThat(result.title).isEqualTo("SprintStart")
        // The workspace's data travels in the metadata payload; the artifact body stays empty.
        assertThat(result.bodyText).isNull()
        // The field means "when ingestion first saw this artifact", not when the workspace was created.
        assertThat(result.createdAtSource).isNull()
        val metadata = result.metadata as BitbucketWorkspaceMetadataArtifactMetadata
        assertThat(metadata.workspace).isEqualTo("sprintstart")
        assertThat(metadata.uuid).isEqualTo("{ws-uuid}")
        assertThat(metadata.name).isEqualTo("SprintStart")
        assertThat(metadata.isPrivate).isTrue()
        assertThat(metadata.createdOn).isEqualTo("2024-01-01T00:00:00Z")
        assertThat(metadata.url).isEqualTo("https://bitbucket.org/sprintstart")
        assertThat(metadata.members).hasSize(1)
        assertThat(metadata.members[0].accountId).isEqualTo("{account-1}")
    }

    @Test
    fun `toCommand maps bitbucket file metadata and hash`() {
        val event = BitbucketFileFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            workspace = "sprintstart",
            slug = "backend",
            path = "src/main/App.kt",
            content = "fun main() = Unit",
            sourceUrl = "https://bitbucket.org/sprintstart/backend/src/rev/src/main/App.kt",
        )

        val result = mapper.toCommand(event)

        assertThat(result.ingestionRunId).isEqualTo(runId)
        assertThat(result.sourceSystem).isEqualTo(SourceSystem.BITBUCKET)
        val metadata = result.metadata as BitbucketArtifactMetadata
        assertThat(metadata.repositoryId).isEqualTo(repositoryId)
        assertThat(metadata.workspace).isEqualTo("sprintstart")
        assertThat(metadata.slug).isEqualTo("backend")
        assertThat(result.sourceId).isEqualTo("bitbucket:sprintstart/backend:FILE:src/main/App.kt")
        assertThat(result.sourceUrl).isEqualTo(event.sourceUrl)
        assertThat(result.artifactType).isEqualTo(ArtifactType.FILE)
        assertThat(result.title).isEqualTo("App.kt")
        assertThat(result.bodyText).isEqualTo("fun main() = Unit")
        assertThat(result.mime).isEqualTo("text/x-kotlin")
        assertThat(result.language).isEqualTo("Kotlin")
        assertThat(result.hash).isEqualTo(event.content.toByteArray().sha256())
    }

    @Test
    fun `toCommand maps commit source and truncates long title`() {
        val subject = "a".repeat(80)
        val event = BitbucketCommitFetchedEvent(
            transactionId = runId,
            repositoryId = repositoryId,
            workspace = "sprintstart",
            slug = "backend",
            author = "Ada",
            committedAt = Instant.parse("2026-03-04T05:06:07Z"),
            sha = "abc123",
            subject = subject,
            sourceUrl = "https://bitbucket.org/sprintstart/backend/commits/abc123",
        )

        val result = mapper.toCommand(event)

        assertThat(result.sourceId).isEqualTo("bitbucket:sprintstart/backend:COMMIT:abc123")
        assertThat(result.sourceUrl).isEqualTo(event.sourceUrl)
        assertThat(result.artifactType).isEqualTo(ArtifactType.COMMIT)
        assertThat(result.title).hasSize(72)
        assertThat(result.bodyText).isEqualTo(subject)
        assertThat(result.createdAtSource).isEqualTo(event.committedAt)
        assertThat(result.hash).isNull()
    }

    @Test
    fun `toCommand maps pull request without hash and with parsed timestamps`() {
        val result = mapper.toCommand(prEvent())

        assertThat(result.sourceId).isEqualTo("bitbucket:sprintstart/backend:PULL_REQUEST:7")
        assertThat(result.sourceUrl).isEqualTo("https://bitbucket.org/sprintstart/backend/pull-requests/7")
        assertThat(result.artifactType).isEqualTo(ArtifactType.PULL_REQUEST)
        assertThat(result.title).isEqualTo("PR #7 Improve docs")
        assertThat(result.state).isEqualTo("MERGED")
        assertThat(result.mergedAtSource).isEqualTo(Instant.parse("2026-03-05T10:00:00Z"))
        assertThat(result.createdAtSource).isEqualTo(Instant.parse("2026-03-02T00:00:00Z"))
        // The field means "when ingestion last saw the source change this", and a cursor-driven
        // re-fetch that found no visible change would move it anyway.
        assertThat(result.updatedAtSource).isNull()
        assertThat(result.hash).isNull()
    }

    @Test
    fun `toCommand assembles the pull request body from description and kept comments`() {
        val result = mapper.toCommand(
            prEvent(
                description = "the description",
                comments = listOf(
                    comment(content = "  first!  ", deleted = false),
                    comment(content = null, deleted = false),
                    comment(content = "gone", deleted = true),
                    comment(content = "second", deleted = false),
                ),
            ),
        )

        // Deleted comments are not conversation, and an empty one carries nothing worth indexing;
        // both are dropped rather than rendered as empty speaker lines. Comments are attributed
        // with the name their own author shows, not the pull request author's.
        assertThat(result.bodyText).isEqualTo(
            "the description\n\nGrace Hopper: first!\n\nGrace Hopper: second",
        )
    }

    @Test
    fun `toCommand yields a null body when the pull request has neither description nor comments`() {
        val result = mapper.toCommand(prEvent(description = null, comments = emptyList()))

        assertThat(result.bodyText).isNull()
    }

    @Test
    fun `toCommand takes the first response from a review or a comment, whichever came first`() {
        val result = mapper.toCommand(
            prEvent(
                participants = listOf(participant(approved = true, participatedOn = "2026-03-04T00:00:00Z")),
                comments = listOf(comment(content = "one thought", createdOn = "2026-03-03T00:00:00Z")),
            ),
        )

        // A newcomer does not experience a comment and a review differently -- both are somebody
        // answering, so the earlier one is the response.
        assertThat(result.firstResponseAtSource).isEqualTo(Instant.parse("2026-03-03T00:00:00Z"))
    }

    @Test
    fun `toCommand does not count the author answering themselves as a response`() {
        val result = mapper.toCommand(
            prEvent(
                authorId = "acc-1",
                comments = listOf(
                    comment(authorId = "acc-1", content = "bumping this", createdOn = "2026-03-03T00:00:00Z"),
                ),
            ),
        )

        assertThat(result.firstResponseAtSource).isNull()
    }

    @Test
    fun `toCommand does not count a deleted comment as a response`() {
        val result = mapper.toCommand(
            prEvent(
                comments = listOf(comment(content = "retracted", deleted = true)),
            ),
        )

        assertThat(result.firstResponseAtSource).isNull()
    }

    @Test
    fun `toCommand does not count a mere participant as a reviewer`() {
        val result = mapper.toCommand(
            prEvent(
                participants = listOf(
                    participant(approved = false, state = null, participatedOn = "2026-03-04T00:00:00Z"),
                ),
            ),
        )

        assertThat(result.firstResponseAtSource).isNull()
    }

    @Test
    fun `toCommand counts change requests by reviewers other than the author`() {
        val result = mapper.toCommand(
            prEvent(
                authorId = "acc-1",
                participants = listOf(
                    participant(authorId = "acc-2", state = "changes_requested", approved = false),
                    participant(authorId = "acc-1", state = "changes_requested", approved = false),
                    participant(authorId = "acc-3", approved = true, state = null),
                ),
            ),
        )

        // The author asking themselves for changes is not the project sending work back, and an
        // approval is not a change request; both are excluded from the count.
        assertThat(result.changesRequestedCount).isEqualTo(1)
    }

    @Test
    fun `toCommand skips a timestamp it cannot parse rather than guessing one`() {
        val result = mapper.toCommand(
            prEvent(
                comments = listOf(comment(content = "no parse", createdOn = "not-a-timestamp")),
            ),
        )

        assertThat(result.firstResponseAtSource).isNull()
    }

    private fun prEvent(
        authorId: String? = "acc-1",
        description: String? = null,
        participants: List<PrParticipant> = emptyList(),
        comments: List<PrComment> = emptyList(),
    ) = BitbucketPullRequestFetchedEvent(
        transactionId = runId,
        repositoryId = repositoryId,
        workspace = "sprintstart",
        slug = "backend",
        number = 7,
        title = "Improve docs",
        state = "MERGED",
        authorId = authorId,
        authorNickname = "ada",
        authorDisplayName = "Ada Lovelace",
        createdOn = "2026-03-02T00:00:00Z",
        updatedOn = "2026-03-05T10:00:00Z",
        description = description,
        closedOn = "2026-03-05T10:00:00Z",
        mergeCommitHash = "merge-sha",
        participants = participants,
        sourceUrl = "https://bitbucket.org/sprintstart/backend/pull-requests/7",
        comments = comments,
    )

    private fun comment(
        authorId: String? = "acc-2",
        content: String? = "text",
        createdOn: String = "2026-03-03T00:00:00Z",
        deleted: Boolean = false,
    ) = PrComment(
        id = 100,
        createdOn = createdOn,
        updatedOn = null,
        content = content,
        authorId = authorId,
        nickname = null,
        displayName = "Grace Hopper",
        deleted = deleted,
    )

    private fun participant(
        authorId: String? = "acc-2",
        approved: Boolean = false,
        state: String? = "approved",
        participatedOn: String? = "2026-03-04T00:00:00Z",
    ) = PrParticipant(
        authorId = authorId,
        nickname = null,
        displayName = null,
        role = "PARTICIPANT",
        approved = approved,
        participatedOn = participatedOn,
        state = state,
    )
}
