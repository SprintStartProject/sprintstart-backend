package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.ArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.BitbucketWorkspaceMetadataArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.GithubOrgMetadataArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Serializes artifact metadata for the store and reads it back.
 *
 * Most wrappers round trip through Jackson's DEDUCTION (see [ArtifactMetadata]), which infers
 * the subtype from the field set. The org-level metadata types are the exception: they are
 * deliberately *not* registered there because their `name` fields subset-match the nested Jira
 * shapes, so they are read back through the explicit methods below instead. Use [fromJson] with
 * the artifact's coordinates so an `ORG_METADATA` row can never reach deduction.
 */
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

    /**
     * Reads stored metadata back, routing org-level rows around DEDUCTION.
     *
     * `ORG_METADATA` payloads are read as their concrete type: the Bitbucket workspace shape for
     * Bitbucket rows, the GitHub org shape for GitHub rows. Every other row goes through the
     * generic deduction path, unchanged.
     *
     * @param json The stored metadata JSON.
     * @param sourceSystem Which connector wrote the row, deciding the org-level shape.
     * @param artifactType The row's type; only `ORG_METADATA` takes the explicit path.
     * @return The deserialized metadata.
     */
    fun fromJson(
        json: String,
        sourceSystem: SourceSystem,
        artifactType: ArtifactType,
    ): ArtifactMetadata {
        if (artifactType != ArtifactType.ORG_METADATA) {
            return fromJson(json)
        }
        return when (sourceSystem) {
            SourceSystem.BITBUCKET -> fromWorkspaceJson(json)
            SourceSystem.GITHUB -> fromGithubOrgJson(json)
            else -> error("No org-level metadata shape for source system $sourceSystem")
        }
    }

    /**
     * Reads a Bitbucket workspace metadata row back as its concrete type.
     *
     * @param json The stored metadata JSON.
     * @return The deserialized workspace metadata.
     */
    fun fromWorkspaceJson(
        json: String,
    ): BitbucketWorkspaceMetadataArtifactMetadata {
        return objectMapper.readValue(json, BitbucketWorkspaceMetadataArtifactMetadata::class.java)
    }

    /**
     * Reads a GitHub org metadata row back as its concrete type.
     *
     * @param json The stored metadata JSON.
     * @return The deserialized org metadata.
     */
    fun fromGithubOrgJson(
        json: String,
    ): GithubOrgMetadataArtifactMetadata {
        return objectMapper.readValue(json, GithubOrgMetadataArtifactMetadata::class.java)
    }
}
