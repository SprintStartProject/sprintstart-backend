package com.sprintstart.sprintstartbackend.canonical.model.response

data class FailedArtifactResponse(
    val artifactIdentifier : String,
    val reason : String,
)
