package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IssueActivityReading
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * The evidence of who worked on the tickets behind a file's changes.
 *
 * A commit that names a ticket, such as `APP-42`, ties the ticket's people to the files it changed: the
 * reporter knew why, the assignee and the commenters discussed how. That is indirect, so a ticket never
 * founds a file. Paths are those the commit changed at the time, so a file renamed since is not joined
 * to its earlier tickets. People with an Atlassian account count as that account, the same one a
 * Confluence page names; others by their name at the tracker.
 */
@Component
class TicketEvidence(
    private val contributions: ContributionEvidenceApi,
) : EvidenceSource {
    override val id: SourceId = SourceId.TICKETS

    override fun histories(projectId: ProjectId, asOf: Instant): List<FileHistory> {
        val ticketsByCommit = contributions.commitTickets(projectId).groupBy({ it.commit }, { it.issueKey })
        if (ticketsByCommit.isEmpty()) return emptyList()
        val peopleByTicket = contributions.issueActivities(projectId, asOf).groupBy { it.issueKey }
        return Preprocessing
            .clean(contributions.fileContributions(projectId, asOf))
            .flatMap { change -> touchesOf(change, ticketsByCommit, peopleByTicket) }
            .groupBy({ it.first }, { it.second })
            .map { (file, touches) -> FileHistory(file.repository, file.path, touches.distinct().sortedBy { it.at }) }
    }

    private fun touchesOf(
        change: FileContribution,
        ticketsByCommit: Map<UUID, List<String>>,
        peopleByTicket: Map<String, List<IssueActivityReading>>,
    ): List<Pair<FileKey, Touch>> {
        val tickets = change.commit?.let(ticketsByCommit::get).orEmpty()
        return tickets
            .flatMap { peopleByTicket[it].orEmpty() }
            .map { FileKey(change.repository, change.path) to touchOf(it) }
    }

    private fun touchOf(activity: IssueActivityReading) =
        Touch(personOf(activity), activity.at, id, founding = false)

    private fun personOf(activity: IssueActivityReading): PersonKey =
        activity.accountId?.let(PersonKey::ofAtlassian) ?: PersonKey("jira:${activity.displayName.trim().lowercase()}")
}
