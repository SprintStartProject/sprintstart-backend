package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscoverRepositoriesResponse(
    val repositories: List<DiscoveredRepository>,
)

@Serializable
data class DiscoveredRepository(
    val name: String,
    @SerialName("private")
    val isPrivate: Boolean,
    @SerialName("html_url")
    val url: String,
    var alreadyConnected: Boolean = false,
    var isEnabled: Boolean? = null,
)
