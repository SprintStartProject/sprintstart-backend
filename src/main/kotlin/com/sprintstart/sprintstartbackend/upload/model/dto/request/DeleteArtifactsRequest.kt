package com.sprintstart.sprintstartbackend.upload.model.dto.request

import jakarta.validation.constraints.NotEmpty
import java.util.UUID

data class DeleteArtifactsRequest(
    @field:NotEmpty
    val artifactIds: Set<UUID>,
    val removerId: UUID,
    val projectId: UUID,
)
