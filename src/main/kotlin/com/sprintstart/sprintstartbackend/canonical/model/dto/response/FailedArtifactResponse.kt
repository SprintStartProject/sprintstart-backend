package com.sprintstart.sprintstartbackend.canonical.model.dto.response

import com.sprintstart.sprintstartbackend.canonical.model.entity.ArtifactType

data class FailedArtifactResponse(
    val sourceId : String?,
    val artifactType: ArtifactType,
    val sourceUrl: String,
    val reason : String,
)
