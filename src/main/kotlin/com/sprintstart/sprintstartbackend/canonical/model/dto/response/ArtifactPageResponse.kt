package com.sprintstart.sprintstartbackend.canonical.model.dto.response

data class ArtifactPageResponse(
    val items: List<ArtifactResponse>,
    val page: PageMetadata
)
