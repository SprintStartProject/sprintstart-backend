package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.connectors.notion.NotionRichText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotionSearchResponse(
    val results: List<NotionPageResponse>,
    @SerialName("next_cursor")
    val nextCursor: String?,
    @SerialName("has_more")
    val hasMore: Boolean,
)

@Serializable
data class NotionPageResponse(
    val id: String,
    val url: String,
    @SerialName("last_edited_time")
    val lastEditedTime: String,
    @SerialName("in_trash")
    val inTrash: Boolean,
    val parent: NotionParentResponse,
    val properties: Map<String, NotionPagePropertyResponse>,
)

@Serializable
data class NotionParentResponse(
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
data class NotionPagePropertyResponse(
    val type: String,
    val title: List<NotionRichText>? = null,
)
