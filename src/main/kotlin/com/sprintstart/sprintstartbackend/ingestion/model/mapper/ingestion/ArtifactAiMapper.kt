package com.sprintstart.sprintstartbackend.ingestion.model.mapper.ingestion

import com.sprintstart.sprintstartbackend.ingestion.model.dto.request.ArtifactAiIngestRequest
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import org.springframework.stereotype.Component

@Component
class ArtifactAiMapper {
    fun toIngestRequest(artifact: Artifact) = ArtifactAiIngestRequest(
        artifactId = artifact.id.toString(),
        sourceSystem = artifact.sourceSystem,
        sourceId = artifact.sourceId,
        sourceUrl = artifact.sourceUrl,
        artifactType = artifact.artifactType,
        title = artifact.title,
        bodyText = artifact.bodyText,
        mime = artifact.mime,
        language = artifact.language,
    )
}
