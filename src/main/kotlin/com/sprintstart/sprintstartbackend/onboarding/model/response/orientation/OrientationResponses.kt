package com.sprintstart.sprintstartbackend.onboarding.model.response.orientation

import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationOrigin
import com.sprintstart.sprintstartbackend.onboarding.external.enums.OrientationStep
import java.time.Instant
import java.util.UUID

/** Where one claim came from, and where the hire can open it. */
data class OrientationCitationResponse(
    val filename: String,
    val chunkId: String,
    val sourceUrl: String?,
)

/** One section of the packet, belonging to exactly one step of the path to a pull request. */
data class OrientationSectionResponse(
    val step: OrientationStep,
    val title: String,
    val body: String,
    val citations: List<OrientationCitationResponse>,
)

/** A piece of existing material the packet drew on. */
data class OrientationSourceResponse(
    val filename: String,
    val sourceUrl: String?,
    val artifactType: String?,
)

data class OrientationPacketResponse(
    val taskId: UUID,
    val taskTitle: String,
    val summary: String?,
    val sections: List<OrientationSectionResponse>,
    val sources: List<OrientationSourceResponse>,
    val assembledAt: Instant,
    /**
     * Who authored this packet. The client badges a [OrientationOrigin.HUMAN] packet as
     * human-written and treats it as authoritative rather than offering to regenerate it.
     */
    val origin: OrientationOrigin,
)

/**
 * Orientation for the task a hire currently has, on one project.
 *
 * Every combination is a real, handled state, and the client must render them as such rather than
 * inventing content to fill the gap:
 * - no [taskId] → the hire has no current task, so there is nothing to orient them for;
 * - a [taskId] with a [packet] → the assembled orientation;
 * - a [taskId] with no [packet] → the corpus had nothing to say about this task, or assembly failed.
 *   [reason] carries what the AI service said. **This is not an error and not an empty packet**: it
 *   is "we could not ground this", and the honest response is to show the task's own sources and a
 *   way to reach a person.
 */
data class MyOrientationResponse(
    val taskId: UUID?,
    val taskTitle: String?,
    val taskUrl: String?,
    val packet: OrientationPacketResponse?,
    val reason: String?,
)
