package com.sprintstart.sprintstartbackend.connectors.notion.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class NotionUserResponse(
    val id: String,
    @SerialName("object")
    val objectType: String,
)
