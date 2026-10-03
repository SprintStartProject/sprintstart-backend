package com.sprintstart.sprintstartbackend.connectors.notion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotionRichText(
    @SerialName("plain_text")
    val plainText: String,
    val href: String? = null,
    val annotations: NotionTextAnnotations? = null,
    val type: String = "text",
)
