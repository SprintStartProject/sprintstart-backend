package com.sprintstart.sprintstartbackend.upload.model.dto.request

import jakarta.validation.constraints.NotNull
import java.util.UUID

data class UploadArtifactsRequest(
    @field:NotNull
    val projectId: UUID,
)
