package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import java.util.UUID

/**
 * A part of the manager's surface the team-mode buddy can open, each with its own tools.
 *
 * @property summary What is inside, in the manager's words. The model chooses an area from its name and
 * this alone, and a name is not enough: "knowledge" does not say that hires' escalated questions live there.
 */
enum class TeamArea(
    val summary: String,
) {
    KNOWLEDGE("questions hires escalated because nobody could answer them, and the canonical answers written for them"),
    STARTER_WORK("the pool of starter-work tasks hires are offered, and GitHub issues that could join it"),
    TEAM("who is on the project and which roles they hold: adding and removing people, giving and taking roles"),
    ARRIVAL("what has to be true before a new hire can start working, as a list the project owns"),
    CONTENT(
        "the onboarding paths of the project's members: their phases, steps, tasks and links, skip requests, " +
            "feedback, knowledge checks and orientation packets",
    ),
    SOURCES("where the project's material comes from: repositories, other connected sources and uploads"),
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
