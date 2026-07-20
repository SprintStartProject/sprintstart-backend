package com.sprintstart.sprintstartbackend.upload.model.dto.response

import java.time.Instant
import java.util.UUID

data class UploadListItemResponse(
    val id: UUID,
    val filename: String,
    val mime: String,
    val uploadedAt: Instant,
)
