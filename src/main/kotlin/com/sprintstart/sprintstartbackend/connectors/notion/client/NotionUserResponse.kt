package com.sprintstart.sprintstartbackend.connectors.notion.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class NotionUserResponse(
    val id: String,
    @SerialName("object")
    val objectType: String,
    val name: String? = null,
    val type: String? = null,
    val bot: NotionBotResponse? = null,
)

@Serializable
internal data class NotionBotResponse(
    @SerialName("workspace_id") val workspaceId: String? = null,
    @SerialName("workspace_name") val workspaceName: String? = null,
)

internal data class NotionTokenIdentity(
    val tokenOwnerId: String,
    val workspaceId: String? = null,
    val workspaceName: String? = null,
)
