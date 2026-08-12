package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * The kind of one page within a [com.sprintstart.sprintstartbackend.onboarding.model.entity.CompetencyModule].
 *
 * A module is presented to the hire as an ordered sequence of pages behind a stepper. The kinds are
 * a rendering contract, not a workflow: an author decides how many of each a module has and in what
 * order, so a competency can be taught as context -> lesson -> walkthrough -> practice -> check, or
 * as a single lesson and a check.
 *
 * Distinct from the retired `StepPageKind`, which described pages *derived* from a per-user step's
 * shape (at most one lesson, one task, one verify) and therefore had nothing an author could edit.
 */
enum class ModulePageKind {
    /** Why this competency matters here, before any instruction. */
    CONTEXT,

    /** A grounded lesson to read. */
    LESSON,

    /** A worked example: the thing being done, start to finish. */
    WALKTHROUGH,

    /** Hands-on practice to work through. Not graded. */
    TASK,

    /** Pointers out to the real material (docs, code, runbooks). */
    RESOURCE,

    /** An ungraded self-check inside the module, for practice before the gate. */
    CHECK,

    /** The graded gate. Renders the module's verification; the client submits via the attempt endpoint. */
    VERIFY,
}
