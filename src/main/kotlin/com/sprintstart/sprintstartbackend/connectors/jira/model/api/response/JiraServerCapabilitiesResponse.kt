package com.sprintstart.sprintstartbackend.connectors.jira.model.api.response

import kotlinx.serialization.Serializable

@Serializable
data class JiraServerCapabilitiesResponse(
    val serverTitle: String,
)
