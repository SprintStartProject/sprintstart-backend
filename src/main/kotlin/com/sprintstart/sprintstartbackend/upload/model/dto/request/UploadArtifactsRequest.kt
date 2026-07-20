package com.sprintstart.sprintstartbackend.upload.model.dto.request

import java.util.UUID

data class UploadArtifactsRequest(
    val uploaderId: UUID,
    val projectId: UUID,
)
