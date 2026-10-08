package com.sprintstart.sprintstartbackend.ingestion.model.dto

/** Stores Notion page structure and source identity inside the artifact metadata JSON. */
data class NotionArtifactMetadata(
    val notionConnectionId: String,
    val notionPageId: String,
    val sections: List<ArtifactSection> = emptyList(),
    val tables: List<String> = emptyList(),
    val codeBlocks: List<ArtifactCodeBlock> = emptyList(),
) : ArtifactMetadata
