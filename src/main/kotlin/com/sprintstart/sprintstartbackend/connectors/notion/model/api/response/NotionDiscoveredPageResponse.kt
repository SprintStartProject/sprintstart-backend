package com.sprintstart.sprintstartbackend.connectors.notion.model.api.response

data class NotionDiscoveredPageResponse(
    val id: String,
    val title: String,
    val url: String,
    val lastEditedTime: String,
)
