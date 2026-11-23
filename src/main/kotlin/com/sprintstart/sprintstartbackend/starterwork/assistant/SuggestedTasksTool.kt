package com.sprintstart.sprintstartbackend.starterwork.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireSnapshotOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.assistant.external.HireToolOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireToolSuggestion
import com.sprintstart.sprintstartbackend.assistant.external.noArgumentsSchema
import com.sprintstart.sprintstartbackend.assistant.external.projectsOf
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/** Good next starter-work tasks for the hire, for the Buddy: ranked by fit, with reasons and a claimable id. */
@Component
@Order(HireToolOrder.SUGGESTED_TASKS)
class SuggestedTasksTool(
    private val userApi: UserApi,
    private val hireStarterWork: HireStarterWorkApi,
) : HireTool {
    override val spec = SPEC

    override val snapshotPosition = HireSnapshotOrder.SUGGESTED_TASKS

    /** Asks for the pool and nothing else, so the mentor does not read it as the path's question. */
    override val suggestion = HireToolSuggestion(
        label = "Anything I can pick up?",
        question = "Is there something in the work pool I could pick up?",
    )

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String = describe(context.hire)

    override fun snapshotFor(hire: UserId): String = "Suggested tasks:\n" + describe(hire)

    private fun describe(hire: UserId): String {
        val projects = userApi.projectsOf(hire)
        if (projects.isEmpty()) {
            return "You are not a member of any project yet, so there are no suggested tasks."
        }
        val sections = projects.mapNotNull { project ->
            val ranked = hireStarterWork
                .rankedTasksFor(hire, ProjectId(project.projectId))
                .take(MAX_SUGGESTED_TASKS)
            if (ranked.isEmpty()) {
                null
            } else {
                buildString {
                    appendLine("On ${project.name}, good next tasks (best first):")
                    ranked.forEach { match ->
                        // The id is how the hire claims one: the claim_goal action names the task
                        // by it, so the suggestion and the claim can never drift onto different
                        // tasks.
                        appendLine("- ${match.task.title} [task_id: ${match.task.id}]")
                        // Show the reasons, never the score: a number is not a reason a hire can act
                        // on, and the ranker exists to explain itself.
                        match.reasons.forEach { appendLine("    · $it") }
                        match.task.sourceUrl?.let { appendLine("    ($it)") }
                    }
                }.trim()
            }
        }
        return sections
            .ifEmpty {
                listOf("There are no starter-work tasks to suggest yet.")
            }.joinToString("\n\n")
    }

    companion object {
        const val GET_SUGGESTED_TASKS = "get_suggested_tasks"
        const val MAX_SUGGESTED_TASKS = 3

        val SPEC = BuddyToolSpecDto(
            name = GET_SUGGESTED_TASKS,
            description = "Good next starter-work tasks for the hire, ranked by fit, each with the " +
                "plain reasons it was suggested and the task_id to claim it by. Use this for " +
                "questions like 'what should I work on?', 'what's a good first task for me?' and " +
                "'is there anything I could pick up?'. The pool is open to them however far along " +
                "their onboarding path they are, so never hold it back until they have got " +
                "further. Present the reasons, never a score. When the hire picks one, offer to " +
                "claim it as their goal with claim_goal. Takes no arguments -- it always ranks " +
                "for the caller.",
            parameters = noArgumentsSchema(),
        )
    }
}
