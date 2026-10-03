package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.connectors.notion.NotionRichText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotionApiSearchResponse(
    val results: List<NotionApiPageResponse>,
    @SerialName("next_cursor")
    val nextCursor: String?,
    @SerialName("has_more")
    val hasMore: Boolean,
)

@Serializable
data class NotionApiPageResponse(
    val id: String,
    val url: String,
    @SerialName("last_edited_time")
    val lastEditedTime: String,
    @SerialName("in_trash")
    val inTrash: Boolean,
    val parent: NotionApiParentResponse,
    val properties: Map<String, NotionApiPagePropertyResponse>,
)

@Serializable
data class NotionApiParentResponse(
    val type: String,
    val workspace: Boolean? = null,
    @SerialName("page_id")
    val pageId: String? = null,
    @SerialName("block_id")
    val blockId: String? = null,
    @SerialName("data_source_id")
    val dataSourceId: String? = null,
    @SerialName("database_id")
    val databaseId: String? = null,
)

@Serializable
data class NotionApiPagePropertyResponse(
    val type: String,
    val title: List<NotionRichText>? = null,
)
