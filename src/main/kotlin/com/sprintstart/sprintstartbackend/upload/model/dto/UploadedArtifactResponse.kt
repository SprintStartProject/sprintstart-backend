package com.sprintstart.sprintstartbackend.upload.model.dto

import java.util.UUID

data class UploadArtifactResponse(

    val id: UUID?,

    val filename: String,

    val status: String,

    val error: String? = null,
)