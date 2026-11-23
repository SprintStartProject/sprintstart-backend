package com.sprintstart.sprintstartbackend.insights.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireSnapshotOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.assistant.external.HireToolOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireToolSuggestion
import com.sprintstart.sprintstartbackend.assistant.external.noArgumentsSchema
import com.sprintstart.sprintstartbackend.assistant.external.projectsOf
import com.sprintstart.sprintstartbackend.insights.external.InsightsReadApi
import com.sprintstart.sprintstartbackend.insights.model.dto.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * How the hire's own work is going, for the Buddy: pull requests, waits, stalls and rework, per project.
 *
 * About their work, not their onboarding: that is the path, and a tool that answered "how is onboarding
 * going" from contributions would send the question to the wrong place.
 */
@Component
@Order(HireToolOrder.METRICS)
class MyMetricsTool(
    private val userApi: UserApi,
    private val insightsRead: InsightsReadApi,
) : HireTool {
    override val spec = SPEC

    override val snapshotPosition = HireSnapshotOrder.PROGRESS

    override val suggestion = HireToolSuggestion(
        label = "How's my work going?",
        question = "How is my work going so far — is anything waiting on someone?",
    )

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String = describe(context.hire)

    override fun snapshotFor(hire: UserId): String = "Progress:\n" + describe(hire)

    private fun describe(hire: UserId): String {
        val projects = userApi.projectsOf(hire)
        if (projects.isEmpty()) {
            return "You are not a member of any project yet, so there are no work metrics."
        }
        val described = projects.mapNotNull { project ->
            insightsRead
                .hireTimeline(hire, ProjectId(project.projectId))
                ?.let { describe(project.name, it) }
        }
        return described
            .ifEmpty { listOf("No work metrics are available for you yet.") }
            .joinToString("\n\n")
    }

    private fun describe(projectName: String, timeline: HireTimelineResponse): String = buildString {
        appendLine("Project: $projectName")
        appendLine("- Open pull requests: ${timeline.openContributionCount}")
        appendLine("- Merged pull requests: ${timeline.acceptedContributionCount}")
        timeline.longestOpenWaitHours?.let {
            appendLine("- Longest pull request currently waiting on a review: $it hours")
        }
        timeline.hoursToFirstResponse?.let {
            appendLine("- Time from your first pull request to its first response: $it hours")
        }
        val stall = if (timeline.stalled) {
            "yes" + (timeline.stalledReason?.let { " ($it)" }.orEmpty())
        } else {
            "no"
        }
        appendLine("- Stalled: $stall")
        appendLine("- Pull requests sent back for changes: ${timeline.returnedContributionCount}")
    }.trim()

    companion object {
        const val GET_MY_METRICS = "get_my_metrics"

        val SPEC = BuddyToolSpecDto(
            name = GET_MY_METRICS,
            description = "How the hire's own work is going on their project(s): open and merged pull " +
                "requests, how long a pull request has been waiting on a review, whether their work " +
                "is stalled, and review rework. Use this for 'is my PR stuck?' or 'how is my work " +
                "going?'. It says nothing about their onboarding -- that is their path. Takes no " +
                "arguments — it always reads the caller.",
            parameters = noArgumentsSchema(),
        )
    }
}
