package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import java.util.UUID

/** A part of the manager's surface the team-mode buddy can open, each with its own tools. */
enum class TeamArea {
    KNOWLEDGE,
    STARTER_WORK,
    TEAM,
    ARRIVAL,
    CONTENT,
    SOURCES,
}

/** Who a team-mode tool runs for, and on which project. Resolved and authorised before any tool runs. */
data class TeamToolContext(
    val userId: UUID,
    val authId: String,
    val projectId: UUID,
)

/**
 * The tools one [TeamArea] contributes to team mode.
 *
 * Areas exist because a manager's whole surface is far more tools than a small model chooses
 * between reliably. The reasoner starts with the team reads and `open_area`, and an area's tools are
 * mounted only once it has been opened — so a tool from an unopened area is one the model was never
 * handed and cannot call.
 *
 * Every implementation must check that each target belongs to [TeamToolContext.projectId]. The
 * caller has already been confirmed as that project's manager; a model-supplied id is a pointer, never
 * an authorisation.
 */
interface TeamAreaTools {
    val area: TeamArea

    fun toolSpecs(): List<BuddyToolSpecDto>

    fun handles(toolName: String): Boolean

    fun execute(call: BuddyToolCallDto, context: TeamToolContext): String
}
