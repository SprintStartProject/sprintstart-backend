package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.ArtifactResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import org.springframework.stereotype.Component

@Component
class ArtifactMapper {
    fun toResponse(artifact: Artifact): ArtifactResponse {
        return ArtifactResponse(
            id = artifact.id,
            title = artifact.title,
            sourceSystem = artifact.sourceSystem,
            sourceUrl = artifact.sourceUrl,
            metadata = artifact.metadata,
            artifactType = artifact.artifactType,
            ingestedAt = artifact.ingestedAt,
        )
    }
}
