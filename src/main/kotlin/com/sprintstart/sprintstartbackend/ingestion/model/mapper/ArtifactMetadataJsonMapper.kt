package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactMetadata
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ArtifactMetadataJsonMapper(
    private val objectMapper: ObjectMapper,
) {
    fun toJson(
        metadata: ArtifactMetadata,
    ): String {
        return objectMapper.writeValueAsString(metadata)
    }

    fun fromJson(
        json: String,
    ): ArtifactMetadata {
        return objectMapper.readValue(json, ArtifactMetadata::class.java)
    }
}
