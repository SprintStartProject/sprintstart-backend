package com.sprintstart.sprintstartbackend.connectors.notion.model.api.response

import java.time.Instant

data class NotionCredentialResponse(
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
)
