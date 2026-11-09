package com.sprintstart.sprintstartbackend.board.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireSnapshotOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.assistant.external.HireToolOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireToolSuggestion
import com.sprintstart.sprintstartbackend.assistant.external.noArgumentsSchema
import com.sprintstart.sprintstartbackend.assistant.external.projectsOf
import com.sprintstart.sprintstartbackend.board.service.OpenPullRequestReader
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * The hire's still-open pull requests, named, for the Buddy: so it can say *which* one is stuck, not just how
 * many. The metrics tool reports the count and the longest wait; this fills in the numbers, titles and links.
 *
 * "Open" means genuinely open, not merely unmerged, so the list matches the count the metrics report. Which
 * pull requests count as open, and which leads, are [OpenPullRequestReader]'s decisions, the same the board's
 * cards show.
 *
 * Mounted only for somebody whose work can be found: without a GitHub login nothing can be attributed to
 * them, and a mentor holding the tool would raise pull requests with a hire who has none.
 */
@Component
@Order(HireToolOrder.OPEN_PULL_REQUESTS)
class OpenPullRequestsTool(
    private val userApi: UserApi,
    private val openPullRequestReader: OpenPullRequestReader,
) : HireTool {
    override val spec = SPEC

    override val snapshotPosition = HireSnapshotOrder.OPEN_PULL_REQUESTS

    /** The one chip that may speak of pull requests: its own mount condition is a GitHub login. */
    override val suggestion = HireToolSuggestion(
        label = "Is my PR stuck?",
        question = "Are any of my pull requests stuck waiting on a review?",
    )

    override fun isMountedFor(hire: UserId): Boolean = !userApi.getGithubLoginByUserId(hire.value).isNullOrBlank()

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String = describe(context.hire)

    /** Left out for a hire with no login: a greeting handed "set a GitHub username" opens by asking for one. */
    override fun snapshotFor(hire: UserId): String? =
        ("Open pull requests:\n" + describe(hire)).takeIf { isMountedFor(hire) }

    private fun describe(hire: UserId): String {
        val login = userApi.getGithubLoginByUserId(hire.value)
        if (login.isNullOrBlank()) {
            return "You haven't set a GitHub username yet, so I can't look up your pull requests. " +
                "Add it on your profile page and I'll be able to list them."
        }
        val projects = userApi.projectsOf(hire)
        if (projects.isEmpty()) {
            return "You are not a member of any project yet, so there are no pull requests to show."
        }
        val sections = projects.mapNotNull { project ->
            val open = openPullRequestReader.openFor(project.projectId, login)
            if (open.isEmpty()) {
                null
            } else {
                buildString {
                    appendLine("On ${project.name}, your open pull requests:")
                    open.forEach { pr -> appendPullRequest(pr) }
                }.trim()
            }
        }
        return sections
            .ifEmpty { listOf("You have no open pull requests right now.") }
            .joinToString("\n\n")
    }

    private fun StringBuilder.appendPullRequest(pr: AuthoredPullRequest) {
        val id = pr.number?.let { "#$it" } ?: "(number unknown)"
        appendLine("- $id ${pr.title ?: "(untitled)"}")
        openPullRequestReader.waitingHours(pr)?.let {
            appendLine("    · waiting $it hours for a first review")
        }
        pr.sourceUrl?.let { appendLine("    ($it)") }
    }

    companion object {
        const val GET_MY_OPEN_PULL_REQUESTS = "get_my_open_pull_requests"

        val SPEC = BuddyToolSpecDto(
            name = GET_MY_OPEN_PULL_REQUESTS,
            description = "The hire's own still-open pull requests (not merged and not closed), " +
                "each with its number, title, link, and how long it has been waiting for a first " +
                "review. Use " +
                "this whenever the hire asks which pull requests they have open, or to name the " +
                "specific pull request that is stuck — `get_my_metrics` only gives the count and " +
                "the longest wait, not the identifiers. Takes no arguments — it always reads the " +
                "caller.",
            parameters = noArgumentsSchema(),
        )
    }
}
