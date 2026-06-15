package com.sprintstart.sprintstartbackend.canonical.model.dto.response

data class FailedArtifactResponse(
    val artifactIdentifier : String,
    val reason : String,
)
