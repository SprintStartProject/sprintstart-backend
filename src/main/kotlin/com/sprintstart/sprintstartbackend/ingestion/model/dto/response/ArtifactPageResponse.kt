package com.sprintstart.sprintstartbackend.ingestion.model.dto.response

data class ArtifactPageResponse(
    val items: List<ArtifactResponse>,
    val page: PageMetadata,
)
