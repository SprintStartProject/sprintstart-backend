package com.sprintstart.sprintstartbackend.onboarding.external.enums

/**
 * Where a hire is on the ramp of real tasks.
 *
 * Onboarding is a ramp of real work, not a course that ends in one — so the stages describe *what
 * kind of task you are on*, not how much curriculum is left. There is no percentage here on
 * purpose: "72% complete" answers a question nobody has, while "you have merged one change and are
 * on your second" answers the one everybody has.
 *
 * Derived on read from facts that already exist (a Task 0 assignment, merged pull requests, a
 * claimed goal). Nothing advances a hire but doing the work.
 */
enum class RampStage {
    /** Mechanics. Proves the branch → PR → review → merge loop, and credits nothing. */
    TASK_ZERO,

    /**
     * A real change in a familiar area, matched to competencies already held. The novelty is the
     * codebase, not the technology.
     */
    TASK_ONE,

    /**
     * Unfamiliar area, or requiring judgement — deliberately past current placement. This is where
     * the buddy and task orientation earn their keep.
     */
    TASK_TWO_PLUS,

    /**
     * A task completed with no buddy intervention and no review rework.
     *
     * Not "all nodes mastered": the exit condition is the honest operational definition of "can be
     * left alone here", and it is directly measurable.
     */
    AUTONOMOUS,
}
