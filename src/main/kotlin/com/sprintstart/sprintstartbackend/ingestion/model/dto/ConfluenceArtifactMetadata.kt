package com.sprintstart.sprintstartbackend.ingestion.model.dto

import com.fasterxml.jackson.annotation.JsonValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stores Confluence page-specific structure inside the artifact metadata JSON.
 *
 * The common artifact entity keeps only shared identity, provenance, and content fields. Headings,
 * Markdown tables, code macros, page-tree relationships, and raw source ACL data are specific to
 * Confluence page ingestion, so they stay in metadata instead of becoming empty columns for other
 * connectors.
 */
@Serializable
data class ConfluenceArtifactMetadata(
    val sections: List<ArtifactSection> = emptyList(),
    val tables: List<String> = emptyList(),
    val codeBlocks: List<ArtifactCodeBlock> = emptyList(),
    val relationships: List<ArtifactRelationship> = emptyList(),
    val sourceAcl: String? = null,
) : ArtifactMetadata

@Serializable
data class ArtifactSection(
    val heading: String,
    val level: Int,
)

@Serializable
data class ArtifactCodeBlock(
    val language: String?,
    val code: String,
)

@Serializable
data class ArtifactRelationship(
    val type: ArtifactRelationshipType,
    val targetSourceArtifactId: String,
)

@Serializable
enum class ArtifactRelationshipType(
    private val jsonValue: String,
) {
    @SerialName("parent_of")
    PARENT_OF("parent_of"),

    @SerialName("child_of")
    CHILD_OF("child_of"),
    ;

    @JsonValue
    fun toJson(): String {
        return jsonValue
    }
}
