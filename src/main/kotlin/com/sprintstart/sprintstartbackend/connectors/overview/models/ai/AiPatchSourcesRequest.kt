package com.sprintstart.sprintstartbackend.connectors.overview.models.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiPatchSourcesRequest(
    val sources: Map<String, Boolean>, // sourceId -> newStatus
)
