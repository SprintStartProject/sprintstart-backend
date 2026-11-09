package com.sprintstart.sprintstartbackend.path.external.model

import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import java.util.UUID

/** The kinds of thing on an onboarding path that the content area can point at. */
enum class PathElementKind(
    val noun: String,
) {
    PHASE("phase"),
    STEP("step"),
    TASK("task"),
    RESOURCE("resource"),
    SKIP("skip request"),
    FEEDBACK("feedback"),
}

/**
 * One thing on somebody's onboarding path, as far as a preview needs to know about it.
 *
 * @property ownerId The person whose path it is. Paths belong to people, not projects, so this is
 * what decides whether a project's manager may touch it.
 * @property position Its place among its siblings, or null when it has none.
 * @property children How many things sit directly under it, which is what a new child's position is
 * validated against.
 * @property contains What deleting it takes with it, in words; empty when it holds nothing.
 * @property siblings How many things share its parent, itself included; what tells a preview whether
 * deleting it moves anything after it.
 * @property finishedSteps How many steps under it the person already got through, finished or skipped.
 * @property stepStatus The status of the step it is or hangs off, or null for a phase.
 */
data class PathElement(
    val kind: PathElementKind,
    val id: UUID,
    val ownerId: UUID,
    val title: String,
    val position: Int?,
    val children: Int,
    val contains: String,
    val stepStatus: StepStatus?,
    val finishedSteps: Int = 0,
    val siblings: Int = 0,
)
