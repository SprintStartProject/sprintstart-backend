package com.sprintstart.sprintstartbackend.ingestion.external

import com.sprintstart.sprintstartbackend.ingestion.external.model.ArtifactDto
import com.sprintstart.sprintstartbackend.ingestion.external.model.ArtifactSourceScope
import java.time.Instant
import java.util.UUID

/**
 * Exported ingestion-module API for other backend modules.
 *
 * Exposes ingestion metadata and source-artifact linking operations without leaking the
 * ingestion module's internal entities. Other modules should depend on this interface instead of
 * querying the ingestion repositories directly.
 */
interface ArtifactIngestionApi {
    /**
     * Returns when a component (`owner/repo`) was first ingested, or null when it has no ingested
     * artifacts.
     */
    fun getFirstIngestedAt(component: String): Instant?

    /**
     * Batch variant of [getFirstIngestedAt]. Only components with a known timestamp are present in
     * the returned map.
     */
    fun getFirstIngestedAt(components: Collection<String>): Map<String, Instant>

    /**
     * Returns whether an ingested artifact with [artifactId] exists.
     */
    fun exists(artifactId: UUID): Boolean

    /**
     * Returns the content hash of an ingested artifact, or null if it has none on record.
     *
     * Callers that need to distinguish "no such artifact" from "artifact has no hash" should check
     * [exists] first.
     */
    fun getHash(artifactId: UUID): String?

    /**
     * Returns whether the artifact exists and belongs to the specified project.
     */
    fun existsInProject(projectId: UUID, artifactId: UUID): Boolean

    /**
     * Links already-ingested artifacts for [sourceScope] to [projectId].
     *
     * The operation does not create artifacts or re-ingest content. It only adds the project to
     * each matching artifact's project set and is safe to call repeatedly for the same source and
     * project. The return value is the number of artifacts matched by the source scope.
     */
    fun linkExistingSourceArtifacts(sourceScope: ArtifactSourceScope, projectId: UUID): Int

    /**
     * Finds and retrieves an artifact by its unique identifier.
     *
     * @param artifactId The unique identifier of the artifact to be retrieved.
     * @return The artifact details wrapped in an [ArtifactDto] object.
     */
    fun findArtifactById(artifactId: UUID): ArtifactDto?
}
