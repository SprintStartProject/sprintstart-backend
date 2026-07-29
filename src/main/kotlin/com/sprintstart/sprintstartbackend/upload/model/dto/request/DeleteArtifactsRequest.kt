package com.sprintstart.sprintstartbackend.upload.model.dto.request

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.util.UUID

data class DeleteArtifactsRequest(
    @field:NotEmpty
    val artifactIds: Set<UUID>,
    @field:NotNull
    val projectId: UUID,
)
