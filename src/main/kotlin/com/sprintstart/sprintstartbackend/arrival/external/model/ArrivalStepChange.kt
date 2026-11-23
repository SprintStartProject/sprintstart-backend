package com.sprintstart.sprintstartbackend.arrival.external.model

import com.sprintstart.sprintstartbackend.arrival.external.enums.Rigor

/** The fields of an arrival step to change; null leaves a field as it is. */
data class ArrivalStepChange(
    val title: String? = null,
    val description: String? = null,
    val href: String? = null,
    val position: Int? = null,
    val settledBy: Rigor? = null,
)
