package com.sprintstart.sprintstartbackend.starterwork.external.model

/** One competency an assessment could place the hire on. */
data class PlacementTopic(
    val key: String,
    val label: String,
    val description: String?,
    val kind: String,
    /** Whether a task the hire could claim right now exercises it. */
    val neededByAvailableWork: Boolean,
)

/** What came of a confirmed placement, and the line to tell the hire. */
data class PlacementResult(
    val recorded: Boolean,
    val message: String,
)
