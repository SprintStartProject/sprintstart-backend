package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.connectors.notion.NotionTableRow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotionBlocksResponse(
    val results: List<NotionBlockResponse>,
    @SerialName("next_cursor")
    val nextCursor: String?,
    @SerialName("has_more")
    val hasMore: Boolean,
)

@Serializable
data class NotionBlockResponse(
    val id: String,
    val type: String,
    @SerialName("has_children")
    val hasChildren: Boolean,
    @SerialName("table_row")
    val tableRow: NotionTableRow? = null,
    val paragraph: NotionTextBlockPayload? = null,
    @SerialName("heading_1")
    val heading1: NotionHeadingPayload? = null,
    @SerialName("heading_2")
    val heading2: NotionHeadingPayload? = null,
    @SerialName("heading_3")
    val heading3: NotionHeadingPayload? = null,
    @SerialName("heading_4")
    val heading4: NotionHeadingPayload? = null,
    @SerialName("bulleted_list_item")
    val bulletedListItem: NotionTextBlockPayload? = null,
    @SerialName("numbered_list_item")
    val numberedListItem: NotionTextBlockPayload? = null,
    @SerialName("to_do")
    val toDo: NotionToDoPayload? = null,
    val quote: NotionTextBlockPayload? = null,
    val callout: NotionTextBlockPayload? = null,
    val toggle: NotionTextBlockPayload? = null,
    val code: NotionCodePayload? = null,
    val table: NotionTablePayload? = null,
    @SerialName("child_page")
    val childPage: NotionChildReferencePayload? = null,
    @SerialName("child_database")
    val childDatabase: NotionChildReferencePayload? = null,
)
