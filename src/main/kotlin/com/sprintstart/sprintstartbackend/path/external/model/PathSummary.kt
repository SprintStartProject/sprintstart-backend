package com.sprintstart.sprintstartbackend.path.external.model

/** How much a reset would remove, and how much of it the hire already got through. */
data class PathSummary(
    val phases: Int,
    val steps: Int,
    val finishedSteps: Int,
    val checks: Int,
)
