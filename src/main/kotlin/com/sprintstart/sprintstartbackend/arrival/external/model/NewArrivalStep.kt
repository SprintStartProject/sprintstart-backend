package com.sprintstart.sprintstartbackend.arrival.external.model

import com.sprintstart.sprintstartbackend.arrival.external.enums.Rigor

/** One step in a batch for `ArrivalAuthoringApi.createAll`, before the scope decides its position. */
data class NewArrivalStep(
    val key: String,
    val title: String,
    val description: String? = null,
    val href: String? = null,
    val settledBy: Rigor = Rigor.DECLARED,
)
