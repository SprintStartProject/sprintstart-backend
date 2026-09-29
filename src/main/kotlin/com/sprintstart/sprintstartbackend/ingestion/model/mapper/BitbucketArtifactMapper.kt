package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.BitbucketPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.PrComment
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.prs.PrParticipant
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.FileMetaDataResolver
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.BitbucketArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildBitbucketSourceId
import com.sprintstart.sprintstartbackend.ingestion.util.sha256
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.format.DateTimeParseException

private const val BITBUCKET_COMMIT_MESSAGE_LENGTH = 72

/** Bitbucket's review state for "please change this before I approve it". */
private const val CHANGES_REQUESTED = "changes_requested"

/**
 * Translates Bitbucket domain events into canonical artifact commands.
 *
 * The Bitbucket counterpart of [GithubArtifactMapper], minus the issue and org-metadata shapes:
 * Bitbucket removed its native issue tracker, so issue-shaped work arrives through Jira instead,
 * and the org/workspace metadata event is not part of the connector's fetch set yet.
 *
 * Commands carry no `authorLogin`. A Bitbucket account id or nickname is not a GitHub login, and
 * `authorLogin` is matched against `User.githubLogin` by the starter-work readers; storing a
 * Bitbucket handle there would let the two identity namespaces alias each other.
 */
@Component
class BitbucketArtifactMapper {
    /**
     * Maps a fetched commit into the canonical command shape.
     *
     * The commit subject is shortened for the title, like the GitHub mapper shortens its message.
     * There is no separate body: commits are read from `git log`, whose shared format carries the
     * subject only, so `bodyText` holds the subject too rather than nothing.
     *
     * @param event The commit event published by the connector.
     * @return The command the artifact provider persists.
     */
    fun toCommand(event: BitbucketCommitFetchedEvent): BitbucketArtifactCommand {
        return BitbucketArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.BITBUCKET,
            sourceId = buildBitbucketSourceId(
                workspace = event.workspace,
                slug = event.slug,
                type = ArtifactType.COMMIT,
                unique = event.sha,
            ),
            sourceUrl = event.sourceUrl,
            artifactType = ArtifactType.COMMIT,
            title = event.subject.take(BITBUCKET_COMMIT_MESSAGE_LENGTH),
            bodyText = event.subject,
            mime = null,
            language = null,
            createdAtSource = event.committedAt,
            updatedAtSource = null,
            hash = null,
            metadata = BitbucketArtifactMetadata(
                repositoryId = event.repositoryId,
                workspace = event.workspace,
                slug = event.slug,
            ),
        )
    }

    /**
     * Maps a fetched repository file and derives metadata from the file name.
     *
     * Content is hashed immediately so the ingestion service can treat file re-fetches as
     * idempotent when the payload is unchanged.
     *
     * @param event The file event published by the connector.
     * @return The command the artifact provider persists.
     */
    fun toCommand(event: BitbucketFileFetchedEvent): BitbucketArtifactCommand {
        val title = event.path.split("/").last()
        val extension = when (title.lowercase()) {
            "dockerfile" -> "dockerfile"
            else -> title.substringAfterLast(".", "").lowercase()
        }

        return BitbucketArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.BITBUCKET,
            sourceId = buildBitbucketSourceId(
                workspace = event.workspace,
                slug = event.slug,
                type = ArtifactType.FILE,
                unique = event.path,
            ),
            sourceUrl = event.sourceUrl,
            artifactType = ArtifactType.FILE,
            title = title,
            bodyText = event.content,
            mime = FileMetaDataResolver.mimeFor(extension),
            language = FileMetaDataResolver.languageFor(extension),
            createdAtSource = null,
            updatedAtSource = null,
            hash = event.content.toByteArray().sha256(),
            metadata = BitbucketArtifactMetadata(
                repositoryId = event.repositoryId,
                workspace = event.workspace,
                slug = event.slug,
            ),
        )
    }

    /**
     * Maps a fetched pull request into the ingestion pull-request representation.
     *
     * Like its GitHub counterpart, the command carries no content hash: the ingestion service
     * treats pull requests as mutable records and compares the stored title and body on re-fetch.
     * `updatedAtSource` is deliberately left null even though Bitbucket reports `updatedOn`: the
     * field means "when ingestion last saw the source change this", and a cursor-driven re-fetch
     * that found no visible change would move it anyway.
     *
     * @param event The pull request event published by the connector.
     * @return The command the artifact provider persists.
     * @throws DateTimeParseException when a timestamp on the event is malformed.
     */
    fun toCommand(event: BitbucketPullRequestFetchedEvent): BitbucketArtifactCommand {
        return BitbucketArtifactCommand(
            ingestionRunId = event.transactionId,
            sourceSystem = SourceSystem.BITBUCKET,
            sourceId = buildBitbucketSourceId(
                workspace = event.workspace,
                slug = event.slug,
                type = ArtifactType.PULL_REQUEST,
                unique = event.number.toString(),
            ),
            sourceUrl = event.sourceUrl,
            artifactType = ArtifactType.PULL_REQUEST,
            title = "PR #${event.number} ${event.title}",
            bodyText = pullRequestBody(event),
            mime = null,
            language = null,
            createdAtSource = Instant.parse(event.createdOn),
            updatedAtSource = null,
            // PRs are always re-ingested on updates; change detection compares title and body.
            hash = null,
            metadata = BitbucketArtifactMetadata(
                repositoryId = event.repositoryId,
                workspace = event.workspace,
                slug = event.slug,
            ),
            // Bitbucket spells its merged state "MERGED", the same word GitHub's state uses, so the
            // value travels unchanged; "merged without a recorded response" keeps meaning unanswered.
            state = event.state,
            mergedAtSource = event.mergeCommitHash?.let { parseOrNull(event.closedOn) },
            firstResponseAtSource = firstResponseAt(event),
            changesRequestedCount = changesRequestedCount(event),
        )
    }

    /**
     * The pull request's body: the description, with the conversation appended as text.
     *
     * The description alone loses the discussion newcomers read to catch up on a pull request, and
     * the AI index only sees `bodyText`. Comments are appended as readable text rather than stored
     * as separate artifacts, because an onboarding question about a pull request is usually about
     * the discussion it carried.
     *
     * @param event The pull request event whose body should be assembled.
     * @return The description followed by every kept comment, or `null` when the pull request has
     *         neither.
     */
    private fun pullRequestBody(event: BitbucketPullRequestFetchedEvent): String? {
        val parts = buildList {
            add(event.description)
            event.comments
                .asSequence()
                .filterNot(PrComment::deleted)
                .map { comment -> commentText(event, comment) }
                .forEach(::add)
        }
        return parts.filterNotNull().joinToString("\n\n").takeIf { it.isNotBlank() }
    }

    /**
     * One comment as readable text, attributed with the name the author shows.
     *
     * @param event The pull request the comment belongs to, used to find its author.
     * @param comment The comment to render.
     * @return The rendered comment, or `null` when the comment is empty.
     */
    private fun commentText(event: BitbucketPullRequestFetchedEvent, comment: PrComment): String? {
        val author = comment.authorName(event)
        val text = comment.content?.trim().takeUnless { it.isNullOrEmpty() } ?: return null
        return "$author: $text"
    }

    private fun PrComment.authorName(event: BitbucketPullRequestFetchedEvent): String =
        displayName ?: nickname ?: if (authorId == event.authorId) "author" else "reviewer"

    /**
     * The earliest response to a pull request from anyone other than its author.
     *
     * Reviews (participants with an approval or a requested-changes decision) and plain comments
     * both count: a newcomer waiting on their first pull request does not experience the two
     * differently. Self-responses are excluded — an author commenting on their own pull request is
     * not the project responding to them — and a comment's timestamp is dropped rather than guessed
     * when it cannot be parsed.
     *
     * @param event The pull request whose first response should be dated.
     * @return The earliest outside response, or `null` when there is none yet.
     */
    private fun firstResponseAt(event: BitbucketPullRequestFetchedEvent): Instant? {
        val reviewTimes = event.participants
            .asSequence()
            .filter(::isReviewDecision)
            .mapNotNull { it.participatedOn }
            .mapNotNull(::parseOrNull)

        val commentTimes = event.comments
            .asSequence()
            .filterNot(PrComment::deleted)
            .filter { comment -> comment.authorId != event.authorId }
            .map(PrComment::createdOn)
            .mapNotNull(::parseOrNull)

        return (reviewTimes + commentTimes).minOrNull()
    }

    /**
     * Whether a participant carries an actual review decision.
     *
     * Bitbucket marks a plain participant as a reviewer through its `changes_requested` state;
     * `PARTICIPANT` role alone is somebody who merely looks, not somebody who responded with work.
     *
     * @param participant The participant to classify.
     * @return `true` when the participant's state represents a review decision.
     */
    private fun isReviewDecision(participant: PrParticipant): Boolean =
        participant.approved || participant.state == "changes_requested"

    /**
     * How many times reviewers asked the author to change this pull request.
     *
     * Counts participants whose state is Bitbucket's `changes_requested`, excluding the author:
     * an author asking themselves for changes is not the project sending work back. Deeper
     * change-request rounds within one participant's latest state are not distinguishable from
     * Bitbucket's payload, so the count is of reviewers, not of rounds.
     *
     * @param event The pull request whose change requests should be counted.
     * @return The number of reviewers currently asking for changes.
     */
    private fun changesRequestedCount(event: BitbucketPullRequestFetchedEvent): Int {
        return event.participants
            .count { participant ->
                participant.authorId != event.authorId && participant.state == CHANGES_REQUESTED
            }
    }

    /**
     * Parses an ISO 8601 timestamp, or yields `null` when it is malformed.
     *
     * @param value The timestamp as the source reported it.
     * @return The instant, or `null` when it cannot be parsed.
     */
    private fun parseOrNull(value: String?): Instant? =
        value?.let { parsed -> runCatching { Instant.parse(parsed) }.getOrNull() }
}
