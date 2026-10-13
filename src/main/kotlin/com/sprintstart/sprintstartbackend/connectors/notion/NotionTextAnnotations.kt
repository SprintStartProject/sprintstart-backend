package com.sprintstart.sprintstartbackend.connectors.notion

import kotlinx.serialization.Serializable

@Serializable
data class NotionTextAnnotations(
    val bold: Boolean,
    val italic: Boolean,
    val strikethrough: Boolean,
    val underline: Boolean,
    val code: Boolean,
    val color: String,
)
