package com.sprintstart.sprintstartbackend.ingestion.model.dto

import java.util.UUID

sealed interface ArtifactMetadata

data class GithubArtifactMetadata(
    val repositoryId: UUID,
    val repositoryFullName: String,
) : ArtifactMetadata

/**
 * `actorId` is operation-neutral: it is the uploader for stored artifact metadata and the remover
 * for failed deletion metadata.
 */
data class UploadArtifactMetadata(
    var storagePath: String? = null,
    var actorId: UUID,
) : ArtifactMetadata
