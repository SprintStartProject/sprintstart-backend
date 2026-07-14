package com.sprintstart.sprintstartbackend.chat.models

import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AiChatFilters(
    @SerialName("source_systems")
    var sourceSystems: List<SourceSystem>?,
    @SerialName("time_from")
    var from: String?,
    @SerialName("time_to")
    var to: String?,
)
