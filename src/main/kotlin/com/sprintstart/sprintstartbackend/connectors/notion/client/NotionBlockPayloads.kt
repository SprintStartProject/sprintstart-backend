package com.sprintstart.sprintstartbackend.connectors.notion.client

import com.sprintstart.sprintstartbackend.connectors.notion.NotionRichText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotionTextBlockPayload(
    @SerialName("rich_text")
    val richText: List<NotionRichText>,
)

@Serializable
data class NotionHeadingPayload(
    @SerialName("rich_text")
    val richText: List<NotionRichText>,
    @SerialName("is_toggleable")
    val isToggleable: Boolean = false,
)

@Serializable
data class NotionToDoPayload(
    @SerialName("rich_text")
    val richText: List<NotionRichText>,
    val checked: Boolean,
)

@Serializable
data class NotionCodePayload(
    @SerialName("rich_text")
    val richText: List<NotionRichText>,
    val language: String,
    val caption: List<NotionRichText> = emptyList(),
)

@Serializable
data class NotionTablePayload(
    @SerialName("table_width")
    val tableWidth: Int,
    @SerialName("has_column_header")
    val hasColumnHeader: Boolean,
    @SerialName("has_row_header")
    val hasRowHeader: Boolean,
)

@Serializable
data class NotionChildReferencePayload(
    val title: String,
)
