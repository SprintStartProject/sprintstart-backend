package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BuddySessionFilters(
    @SerialName("source_systems")
    var sourceSystems: List<SourceSystem>?,
    @SerialName("time_from")
    var from: String?,
    @SerialName("time_to")
    var to: String?,
)
