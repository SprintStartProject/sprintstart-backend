package com.sprintstart.sprintstartbackend.teamspace.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import java.util.UUID

/**
 * A part of the manager's surface the team-mode buddy can open, each with its own tools.
 *
 * @property summary What is inside, in the manager's words. The model picks an area from its name and
 * this alone.
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

/** Who a team-mode tool runs for, and on which project; authorised before any tool runs. */
data class TeamToolContext(
    val userId: UUID,
    val authId: String,
    val projectId: UUID,
    /** The app path the manager was on when they spoke; null when not sent. */
    val currentPage: String? = null,
)

/**
 * The tools one [TeamArea] contributes to team mode.
 *
 * Areas exist because a manager's whole surface is more tools than a small model chooses between reliably.
 * An area's tools are mounted only once it has been opened, so the model cannot call a tool it was never
 * handed.
 *
 * Every implementation must check that each target belongs to [TeamToolContext.projectId]: a
 * model-supplied id is a pointer, never an authorisation.
 */
interface TeamAreaTools {
    val area: TeamArea

    fun toolSpecs(): List<BuddyToolSpecDto>

    fun handles(toolName: String): Boolean

    fun execute(call: BuddyToolCallDto, context: TeamToolContext): String
}
