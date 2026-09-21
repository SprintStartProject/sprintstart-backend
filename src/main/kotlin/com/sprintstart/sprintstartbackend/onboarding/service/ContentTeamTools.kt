package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The read tools of team mode's content area: what is on the onboarding paths of the project's
 * members.
 *
 * Paths belong to people, not projects, so everything here starts from a member of the turn's project
 * and reads only what is on that member's path. The ids these print are how the content actions name
 * their targets, and every action checks again that its target is on a member's path.
 */
@Component
class ContentTeamTools(
    private val scope: ContentScope,
    private val onboardingPathService: OnboardingPathService,
    private val onboardingStepService: OnboardingStepService,
) : TeamAreaTools {
    override val area = TeamArea.CONTENT

    override fun toolSpecs(): List<BuddyToolSpecDto> = listOf(GET_MEMBER_PATH_SPEC)

    override fun handles(toolName: String): Boolean = toolName == GET_MEMBER_PATH

    override fun execute(call: BuddyToolCallDto, context: TeamToolContext): String =
        when (call.name) {
            GET_MEMBER_PATH -> memberPath(call.uuidArgument("member_id"), context.projectId)
            else -> "Unknown tool: ${call.name}."
        }

    /**
     * One member's whole path, phases to tasks, each with the id an action needs.
     *
     * Descriptions are cut short: the ids are what a manager's request is turned into, and the
     * full text of every step of a long path would bury them.
     */
    private fun memberPath(memberId: UUID?, projectId: UUID): String {
        val owner = scope.member(memberId, projectId) ?: return NOT_A_MEMBER_HERE
        val path = runCatching { onboardingPathService.getOnboardingPathByUserId(owner.userId) }
            .getOrElse {
                if (it !is ResponseStatusException) throw it
                return "${owner.displayName} has no onboarding path yet."
            }

        return buildString {
            appendLine("${owner.displayName}'s onboarding path:")
            scope.sharedNote(owner, projectId).takeIf { it.isNotEmpty() }?.let { appendLine(it) }
            path.phases.sortedBy { it.position }.forEach { phase ->
                appendLine()
                appendLine("Phase ${phase.position + 1}: ${phase.title} [phase_id: ${phase.id}]")
                phase.description.takeIf { it.isNotBlank() }?.let { appendLine("  ${it.short()}") }
                onboardingStepService.getOnboardingStepsByPhaseId(phase.id).sortedBy { it.position }.forEach { step ->
                    appendLine(
                        "  Step ${step.position + 1}: ${step.title} [step_id: ${step.id}] — " +
                            "${step.type.name.lowercase()}, ${step.estimatedMinutes} min, ${step.status.said()}",
                    )
                    step.description.takeIf { it.isNotBlank() }?.let { appendLine("    ${it.short()}") }
                    step.tasks.sortedBy { it.position }.forEach { task ->
                        appendLine("    - [${if (task.finished) "x" else " "}] ${task.title} [task_id: ${task.id}]")
                    }
                    step.resources.forEach { resource ->
                        appendLine("    - link: ${resource.title} <${resource.url}> [resource_id: ${resource.id}]")
                    }
                }
            }
        }.trim()
    }

    private fun StepStatus.said(): String = name.lowercase().replace('_', ' ')

    private fun String.short(): String = if (length <= SHORT_CHARS) this else take(SHORT_CHARS - 1).trimEnd() + "…"

    companion object {
        const val GET_MEMBER_PATH = "get_member_path"

        private const val SHORT_CHARS = 200

        val GET_MEMBER_PATH_SPEC = BuddyToolSpecDto(
            name = GET_MEMBER_PATH,
            description = "One project member's whole onboarding path: its phases, their steps, and each " +
                "step's tasks and links, every one with the id an action needs. Also says where each step " +
                "stands for them. Use the member_id from find_member. A path belongs to the person, not to " +
                "this project, so it shows what they have across all of theirs.",
            parameters = stringFields("member_id" to "The member_id from find_member.", required = listOf("member_id")),
        )
    }
}
