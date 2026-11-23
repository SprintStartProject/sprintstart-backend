package com.sprintstart.sprintstartbackend.assistant.external

import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectDto
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Who a hire tool runs for, and where in the app they were when they asked. */
data class HireToolContext(
    val hire: UserId,
    /** The app path the hire was on when they spoke; null when not sent. */
    val currentPage: String? = null,
)

/**
 * A read-only tool a feature lends the hire's Buddy, from `<feature>/assistant/`.
 *
 * The Buddy mounts every bean of this type that [isMountedFor] the caller, in `@Order` order (see
 * [HireToolOrder]), because the spec order is the order the reasoner sees. A tool only ever reads the
 * caller: [HireToolContext.hire] is the authenticated hire, never an id the model supplied.
 */
interface HireTool {
    val spec: BuddyToolSpecDto

    /**
     * Whether to hand this tool to [hire] at all. "Absent, never empty": a tool whose answer would be
     * "nothing" for this hire is not mounted, because a mentor holding it finds something to say anyway.
     */
    fun isMountedFor(hire: UserId): Boolean = true

    fun execute(call: BuddyToolCallDto, context: HireToolContext): String

    /** This tool's part of the opening greeting's snapshot of the hire, or null to add nothing. */
    fun snapshotFor(hire: UserId): String? = null

    /**
     * Where [snapshotFor]'s section sits in the greeting, lowest first (see [HireSnapshotOrder]). The
     * greeting has an order of its own: it opens on what is missing, then how work is going.
     */
    val snapshotPosition: Int get() = Int.MAX_VALUE

    /**
     * The chip offered for this tool whenever it is mounted, or null for none. Adding a tool never
     * silently adds a chip: the wording for a hire is a separate decision from what the mentor can read.
     */
    val suggestion: HireToolSuggestion? get() = null
}

/**
 * A suggestion chip. [label] is what the chip says; [question] is what lands in the composer.
 *
 * Wording may only assume what the tool's mount condition guarantees. A chip is offered exactly when its
 * tool is mounted, so a chip whose tool mounts for everybody must be true of a designer, a Scrum Master and
 * a developer alike: no "clone", "repository", "commit" or "pull request" in it. The label and the question
 * differ only in length; a chip that promised something other than the question it writes would be putting
 * words in the hire's mouth.
 */
data class HireToolSuggestion(
    val label: String,
    val question: String,
)

/**
 * Where each feature's hire tools sit in the list the reasoner sees, lowest first. Gaps leave room to
 * place a new tool between two others without renumbering.
 */
object HireToolOrder {
    /** The path *is* the onboarding, and the Buddy the tutor along it: first, before every other tool. */
    const val PATH = 100

    /** What has to be true before the hire can work: before every other state tool. */
    const val ARRIVAL = 200
    const val METRICS = 300
    const val COMPETENCIES = 400
    const val COMPETENCIES_TO_ASSESS = 500
    const val OPEN_PULL_REQUESTS = 600
    const val SUGGESTED_TASKS = 700
    const val CANONICAL_ANSWERS = 800
    const val TEAMMATES = 900

    /** After the hire's state: it is about the app rather than the hire's onboarding. */
    const val APP_GUIDE = 1000
    const val PLACE_CARD = 1100
    const val READ_BOARD = 1200
}

/** Where each section of the opening greeting's snapshot sits, lowest first. */
object HireSnapshotOrder {
    /** The greeting opens on the onboarding. */
    const val PATH = 100

    /** Setup before progress: a greeting grounded in progress greets a hire who cannot get in. */
    const val ARRIVAL = 200
    const val PROGRESS = 300
    const val OPEN_PULL_REQUESTS = 400
    const val SUGGESTED_TASKS = 500
    const val COMPETENCIES = 600
    const val UNPLACED = 700
}

/** The parameter schema of a tool that takes no arguments. */
fun noArgumentsSchema(): JsonObject =
    buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { })
    }

/** The projects [hire] is a member of; empty when they are on none or do not exist. */
fun UserApi.projectsOf(hire: UserId): Set<ProjectDto> =
    getUsersByIds(listOf(hire.value)).firstOrNull()?.projects.orEmpty()
