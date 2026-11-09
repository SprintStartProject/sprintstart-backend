package com.sprintstart.sprintstartbackend.arrival.service

import com.sprintstart.sprintstartbackend.arrival.external.enums.Rigor
import com.sprintstart.sprintstartbackend.arrival.model.entity.ArrivalStep
import java.time.Instant

/**
 * One arrival step as it applies to one hire: the shared definition, plus whether they have settled
 * it.
 *
 * [settledAt] and [rigor] are null together and mean "not settled yet", which is a normal state on
 * day one rather than missing data.
 */
data class ResolvedArrivalStep(
    val step: ArrivalStep,
    val settledAt: Instant?,
    val rigor: Rigor?,
    /**
     * The name of the project this step is scoped to; null for a company-wide step.
     *
     * The client groups under this name, so it must be a name rather than an id — a hire's
     * list is a union across their projects, and the same title can appear once per project.
     */
    val projectName: String? = null,
) {
    val settled: Boolean get() = settledAt != null
}
