package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.AssignedIssue
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraArtifactMetadataWrapper
import com.sprintstart.sprintstartbackend.ingestion.model.dto.JiraIssueHistoryItem
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.ArtifactMetadataJsonMapper
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Turns an ingested tracked issue into the four moments onboarding measures.
 *
 * A pull request arrives with its lifecycle flattened into columns; a tracked issue is a changelog
 * to be walked, and this is the only part of Jira evidence with any judgement in it.
 *
 * Comments carry no timestamp. The ingested `JiraIssueComment` has an author and a body and
 * nothing else, so first response is *the first time somebody other than the assignee touched the
 * issue* — a changelog entry of theirs, which is narrower than a comment and what the data supports.
 *
 * "Done" is a status category, not a resolution. Jira files "Won't Do" under the same Done
 * category as "Done" and the connector does not parse the resolution field, so an abandoned issue
 * and a finished one are indistinguishable here. Nothing is reported as abandoned — the
 * alternative is guessing from status *names*, which are whatever each team typed.
 */
@Component
class AssignedIssueReader(
    private val artifactMetadataJsonMapper: ArtifactMetadataJsonMapper,
) {
    private companion object {
        const val STATUS_FIELD = "status"
        const val ASSIGNEE_FIELD = "assignee"

        // Jira's own category name for "finished", independent of whatever a team called the
        // status itself -- one board's "Done" is another's "Shipped" or "Akzeptiert".
        const val DONE_CATEGORY = "Done"
    }

    /**
     * The issue as [assigneeDisplayName]'s, or null when it is not theirs.
     *
     * Null rather than an empty result, so the caller can filter one list rather than reason about
     * two kinds of nothing.
     */
    fun read(artifact: Artifact, assigneeDisplayName: String): AssignedIssue? {
        val metadata = artifactMetadataJsonMapper.fromJson(artifact.metadata) as? JiraArtifactMetadataWrapper
            ?: return null
        if (!metadata.assignee?.displayName.equals(assigneeDisplayName, ignoreCase = true)) {
            return null
        }

        val history = metadata.history.historyItems.sortedBy { it.createdAt }
        val statusChanges = history.filter { item -> item.touches(STATUS_FIELD) }
        val openedAt = assignedAt(history, assigneeDisplayName) ?: artifact.createdAtSource
        val accepting = acceptingChange(metadata, statusChanges, assigneeDisplayName)

        return AssignedIssue(
            artifactId = artifact.id,
            openedAt = openedAt,
            firstResponseAt = firstResponseAt(history, assigneeDisplayName, openedAt),
            acceptedAt = accepting?.createdAt,
            // The accepting move is excluded rather than filtered out inside the walk: it is also
            // somebody else moving the issue out of a status the hire put it in, and counting the
            // acceptance as a send-back would mean no tracked issue could ever have a clean run.
            returnedCount = returnedCount(statusChanges.filterNot { it === accepting }, assigneeDisplayName),
            key = metadata.issueKey,
            title = artifact.title,
            sourceUrl = artifact.sourceUrl,
        )
    }

    /**
     * When the issue became theirs.
     *
     * The *first* time it was assigned to them rather than the most recent: a hire who was handed a
     * piece of work, lost it and got it back has been waiting since the first hand-over, and taking
     * the latest would quietly reset their clock every time somebody reassigned around them.
     */
    private fun assignedAt(history: List<JiraIssueHistoryItem>, assignee: String): Instant? =
        history
            .firstOrNull { item ->
                item.items.any {
                    it.field.equals(ASSIGNEE_FIELD, ignoreCase = true) &&
                        it.to.equals(assignee, ignoreCase = true)
                }
            }?.createdAt

    /**
     * The first time somebody other than the assignee touched the issue after it became theirs.
     *
     * Anything at all counts — a status move, a field edit, a re-assignment — because a newcomer
     * does not experience the kinds differently: what they are waiting for is a sign that somebody
     * else has looked.
     */
    private fun firstResponseAt(
        history: List<JiraIssueHistoryItem>,
        assignee: String,
        openedAt: Instant?,
    ): Instant? =
        history
            .firstOrNull { item ->
                !item.author.displayName.equals(assignee, ignoreCase = true) &&
                    (openedAt == null || !item.createdAt.isBefore(openedAt))
            }?.createdAt

    /**
     * The changelog entry in which somebody else accepted it, or null.
     *
     * Null when the assignee moved their own issue to Done. Closing your own ticket is a
     * claim, and this source exists precisely to produce evidence nobody had to vouch for — the
     * same rule that stops a hire attesting their own work. Such an issue stays in flight rather
     * than being recorded as a weaker acceptance: absent evidence stays "no evidence", and a
     * downgraded one would still be counted somewhere.
     *
     * Null too when the issue is not in a done status at all, and when it reached one with no
     * changelog behind it — an issue that was already Done before this project connected its
     * tracker was not accepted *here*, and dating it from the ingest would invent a moment.
     */
    private fun acceptingChange(
        metadata: JiraArtifactMetadataWrapper,
        statusChanges: List<JiraIssueHistoryItem>,
        assignee: String,
    ): JiraIssueHistoryItem? {
        if (!metadata.statusCategory.equals(DONE_CATEGORY, ignoreCase = true)) {
            return null
        }
        val settling = statusChanges.lastOrNull() ?: return null
        if (settling.author.displayName.equals(assignee, ignoreCase = true)) {
            return null
        }
        return settling
    }

    /**
     * How many times somebody else moved the issue out of a status the assignee had put it in.
     *
     * The tracker's version of a review asking for changes: the assignee said it was ready, and
     * somebody who was not them moved it back. Counting every status change instead would count the
     * normal flow of work as rework, and reporting a flat zero would hand every tracked issue the
     * clean-run half of the autonomy signal without it having been earned.
     */
    private fun returnedCount(statusChanges: List<JiraIssueHistoryItem>, assignee: String): Int {
        var returned = 0
        var lastMovedByAssignee = false
        statusChanges.forEach { change ->
            val byAssignee = change.author.displayName.equals(assignee, ignoreCase = true)
            if (!byAssignee && lastMovedByAssignee) {
                returned++
            }
            lastMovedByAssignee = byAssignee
        }
        return returned
    }

    private fun JiraIssueHistoryItem.touches(field: String): Boolean =
        items.any { it.field.equals(field, ignoreCase = true) }
}
