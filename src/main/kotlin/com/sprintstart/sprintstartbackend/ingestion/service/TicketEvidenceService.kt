package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.CommitTicket
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.JiraArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IssueActivity
import com.sprintstart.sprintstartbackend.ingestion.repository.CommitMessageRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.IssueActivityRepository
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Keeps who worked on which ticket, and finds the tickets commits name, so the people behind a ticket
 * can be credited with the files its commits changed.
 */
@Service
class TicketEvidenceService(
    private val activities: IssueActivityRepository,
    private val messages: CommitMessageRepository,
) {
    /**
     * Records who worked on one ticket, in place of what was recorded before.
     *
     * The reporter and the creator count at the ticket's creation, the assignee at its last update, each
     * changelog entry at its time, and each comment at its time or, when the tracker sent none, at the
     * ticket's last update.
     */
    @Transactional
    fun record(issue: Artifact, command: JiraArtifactCommand) {
        activities.deleteAllByArtifactId(issue.id)
        val created = listOf(command.createdBy, command.reportedBy).map { it to command.createdAt }
        val assigned = listOfNotNull(command.assignee).map { it to command.updatedAt }
        val changed = command.history.historyItems.map { it.author to it.createdAt }
        val commented = command.comments.map { it.author to (it.createdAt ?: command.updatedAt) }
        val rows = (created + assigned + changed + commented)
            .distinctBy { (author, at) -> (author.accountId ?: author.displayName) to at }
            .map { (author, at) ->
                IssueActivity(
                    null,
                    issue,
                    command.issueKey,
                    author.accountId,
                    author.displayName,
                    at,
                )
            }
        activities.saveAll(rows)
    }

    /** The tickets the commits of a project name in their messages, each pair once. */
    @Transactional(readOnly = true)
    fun commitTickets(projectId: ProjectId): List<CommitTicket> =
        messages.findMessages(projectId.value).flatMap { commit ->
            ISSUE_KEY
                .findAll(commit.message.orEmpty())
                .map { CommitTicket(commit.commit, it.value) }
                .distinct()
                .toList()
        }

    private companion object {
        /** A Jira key: a project key of capitals and digits, a dash and a number, as in `APP-42`. */
        val ISSUE_KEY = Regex("""\b[A-Z][A-Z0-9]+-\d+\b""")
    }
}
