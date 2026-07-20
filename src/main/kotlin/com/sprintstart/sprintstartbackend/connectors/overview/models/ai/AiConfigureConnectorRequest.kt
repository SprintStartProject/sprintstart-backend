package com.sprintstart.sprintstartbackend.connectors.overview.models.ai

import kotlinx.serialization.Serializable

@Serializable
data class AiConfigureConnectorRequest(
    val enabled: Boolean,
)
