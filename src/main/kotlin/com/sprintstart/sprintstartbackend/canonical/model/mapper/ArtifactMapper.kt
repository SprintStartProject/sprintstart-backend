package com.sprintstart.sprintstartbackend.canonical.model.mapper

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.ArtifactResponse
import com.sprintstart.sprintstartbackend.canonical.model.entity.Artifact
import org.springframework.stereotype.Component

@Component
class ArtifactMapper {
    fun toResponse(artifact: Artifact): ArtifactResponse {
        return ArtifactResponse(
            id = artifact.id,
            title = artifact.title,
            sourceSystem = artifact.sourceSystem,
            sourceUrl = artifact.sourceUrl,
            repositoryFullName = artifact.repositoryFullName,
            artifactType = artifact.artifactType,
        )
    }
}
