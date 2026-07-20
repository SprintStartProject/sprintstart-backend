package com.sprintstart.sprintstartbackend.connectors.overview.models.api.response

data class PatchSourcesOfConnectorResponse(
    val connectorId: String,
    val sources: List<PatchedSource>,
)

data class PatchedSource(
    val id: String,
    val name: String,
    val url: String,
    val enabled: Boolean,
)
