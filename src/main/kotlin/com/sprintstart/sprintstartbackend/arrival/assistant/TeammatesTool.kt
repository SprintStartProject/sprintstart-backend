package com.sprintstart.sprintstartbackend.arrival.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.assistant.external.HireToolOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireToolSuggestion
import com.sprintstart.sprintstartbackend.assistant.external.noArgumentsSchema
import com.sprintstart.sprintstartbackend.assistant.external.projectsOf
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Who else is on the hire's projects, by id and name, for the Buddy: the people a hire can ask to attest
 * their work.
 *
 * The hire themselves is left out: an attestation confirmed by the person who did the work is not evidence,
 * and never showing the option is what stops the buddy proposing it.
 */
@Component
@Order(HireToolOrder.TEAMMATES)
class TeammatesTool(
    private val userApi: UserApi,
    private val projectMembershipApi: ProjectMembershipApi,
) : HireTool {
    override val spec = SPEC

    /**
     * Stops at naming people: whether that ends in request_attestation is the mentor's call and the hire's
     * confirmation, not the chip's.
     */
    override val suggestion = HireToolSuggestion(
        label = "Who's on my team?",
        question = "Who else is on my project?",
    )

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String {
        val projects = userApi.projectsOf(context.hire)
        if (projects.isEmpty()) {
            return "You are not a member of any project yet, so there are no teammates to name."
        }
        val sections = projects.map { project ->
            val others = projectMembershipApi
                .getProjectMembers(project.projectId)
                .filter { it.userId != context.hire.value }
            val lines = others
                .map { "- ${it.displayName} (id: ${it.userId})" }
                .ifEmpty { listOf("- nobody else is on this project yet") }
            (listOf("Project: ${project.name}") + lines).joinToString("\n")
        }
        return sections.joinToString("\n\n")
    }

    companion object {
        const val GET_TEAMMATES = "get_teammates"

        val SPEC = BuddyToolSpecDto(
            name = GET_TEAMMATES,
            description = "The other people on the hire's projects, with their ids. Read this " +
                "before offering request_attestation, so the hire can pick who should confirm " +
                "their work and you pass that person's real id. The hire is never in this list — " +
                "work confirmed by the person who did it is not evidence.",
            parameters = noArgumentsSchema(),
        )
    }
}
