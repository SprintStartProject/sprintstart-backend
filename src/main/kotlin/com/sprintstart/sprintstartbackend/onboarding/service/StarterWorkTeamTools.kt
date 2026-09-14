package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CandidatePoolState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkCandidateResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The read tools of team mode's starter-work area: the pool of tasks hires can be suggested, the part
 * of it nobody has vouched for, and the open issues that could join it.
 *
 * All three show only work from repositories linked to the turn's project, through [StarterWorkScope].
 * The ids they print are how the starter-work actions name their targets, and every action asks the
 * scope again.
 */
@Component
class StarterWorkTeamTools(
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService,
    private val starterWorkScope: StarterWorkScope,
) : TeamAreaTools {
    override val area = TeamArea.STARTER_WORK

    override fun toolSpecs(): List<BuddyToolSpecDto> =
        listOf(LIST_STARTER_WORK_POOL_SPEC, LIST_UNREVIEWED_STARTER_WORK_SPEC, LIST_STARTER_WORK_CANDIDATES_SPEC)

    override fun handles(toolName: String): Boolean = toolName in NAMES

    override fun execute(call: BuddyToolCallDto, context: TeamToolContext): String =
        when (call.name) {
            LIST_STARTER_WORK_POOL -> pool(context.projectId)
            LIST_UNREVIEWED_STARTER_WORK -> unreviewed(context.projectId)
            LIST_STARTER_WORK_CANDIDATES -> candidates(context.projectId, call.textArgument("search"))
            else -> "Unknown tool: ${call.name}."
        }

    private fun pool(projectId: UUID): String {
        val live = starterWorkTaskProposalRepository.findAllByStatus(ProposalStatus.LIVE).sortedBy { it.title }
        val tasks = starterWorkScope.onProject(live, projectId) { it.sourceId }
        if (tasks.isEmpty()) {
            return "The starter-work pool has no tasks from this project's repositories."
        }
        return tasks.listed("Starter-work tasks from this project's repositories, by title:")
    }

    private fun unreviewed(projectId: UUID): String {
        val live = starterWorkTaskProposalRepository
            .findAllByStatusAndReviewedFalse(ProposalStatus.LIVE)
            .sortedBy { it.createdAt }
        val tasks = starterWorkScope.onProject(live, projectId) { it.sourceId }
        if (tasks.isEmpty()) {
            return "Nothing from this project's repositories is waiting for a review."
        }
        return tasks.listed(
            "Starter-work tasks from this project's repositories that nobody has reviewed, oldest first. " +
                "Hires can already be suggested them; they rank lower until reviewed:",
        )
    }

    private fun List<StarterWorkTaskProposal>.listed(heading: String): String {
        val tasks = this
        return buildString {
            appendLine(heading)
            tasks.take(LIST_LIMIT).forEach { task ->
                append("- “${task.title}” [task_id: ${task.id}] — ${githubRepositoryOf(task.sourceId)}")
                append(if (task.reviewed) ", reviewed" else ", not reviewed")
                if (task.taskZeroEligible) append(", Task 0 candidate")
                appendLine()
                task.summary?.let { appendLine("  Note: $it") }
                task.sourceUrl?.let { appendLine("  $it") }
            }
            if (tasks.size > LIST_LIMIT) appendLine("…and ${tasks.size - LIST_LIMIT} more.")
        }.trim()
    }

    /** Open issues only, and only those not already in the pool or removed from it: promotable ones. */
    private fun candidates(projectId: UUID, search: String): String {
        val open = starterWorkTaskProposalService
            .listCandidates(projectId)
            .filter { it.poolState == CandidatePoolState.AVAILABLE }
        val matching = starterWorkScope
            .onProject(open, projectId) { it.sourceId }
            .filter { search.isBlank() || it.matches(search) }
        if (matching.isEmpty()) {
            return if (search.isBlank()) {
                "This project's repositories have no open issue that is not already in the starter-work pool."
            } else {
                "No open issue outside the starter-work pool matches “$search”."
            }
        }
        return buildString {
            appendLine(
                "Open issues from this project's repositories that are not in the starter-work pool, " +
                    "most recently changed first:",
            )
            matching.take(LIST_LIMIT).forEach { issue ->
                append("- “${issue.title}” [source_id: ${issue.sourceId}] — ${githubRepositoryOf(issue.sourceId)}")
                if (issue.labels.isNotEmpty()) append(", labels: ${issue.labels.joinToString()}")
                appendLine()
                issue.excerpt?.let { appendLine("  ${it.take(EXCERPT_CHARS).replace('\n', ' ').trim()}…") }
            }
            if (matching.size > LIST_LIMIT) {
                appendLine("…and ${matching.size - LIST_LIMIT} more. Pass search to narrow them down.")
            }
        }.trim()
    }

    private fun StarterWorkCandidateResponse.matches(search: String): Boolean =
        title.contains(search, ignoreCase = true) || labels.any { it.contains(search, ignoreCase = true) }

    companion object {
        const val LIST_STARTER_WORK_POOL = "list_starter_work_pool"
        const val LIST_UNREVIEWED_STARTER_WORK = "list_unreviewed_starter_work"
        const val LIST_STARTER_WORK_CANDIDATES = "list_starter_work_candidates"

        private val NAMES = setOf(LIST_STARTER_WORK_POOL, LIST_UNREVIEWED_STARTER_WORK, LIST_STARTER_WORK_CANDIDATES)

        private const val LIST_LIMIT = 40
        private const val EXCERPT_CHARS = 160

        val LIST_STARTER_WORK_POOL_SPEC = BuddyToolSpecDto(
            name = LIST_STARTER_WORK_POOL,
            description = "Every starter-work task hires on this project can be suggested, with its task_id, its " +
                "repository, whether somebody reviewed it and whether it is a Task 0 candidate. Read it before " +
                "removing a task or changing its Task 0 flag. Takes no arguments — it always reads this project.",
            parameters = stringFields(required = emptyList()),
        )

        val LIST_UNREVIEWED_STARTER_WORK_SPEC = BuddyToolSpecDto(
            name = LIST_UNREVIEWED_STARTER_WORK,
            description = "The starter-work tasks on this project nobody has reviewed yet, oldest first, each with " +
                "its task_id. Use it for 'what needs reviewing?' or before marking one reviewed. Takes no " +
                "arguments — it always reads this project.",
            parameters = stringFields(required = emptyList()),
        )

        val LIST_STARTER_WORK_CANDIDATES_SPEC = BuddyToolSpecDto(
            name = LIST_STARTER_WORK_CANDIDATES,
            description = "Open issues from this project's repositories that are not in the starter-work pool, " +
                "each with the source_id to put it there. Use it when the manager wants to add work for " +
                "newcomers. It always reads this project.",
            parameters = stringFields(
                "search" to "Optional: part of an issue title or label, to narrow the list.",
                required = emptyList(),
            ),
        )
    }
}
