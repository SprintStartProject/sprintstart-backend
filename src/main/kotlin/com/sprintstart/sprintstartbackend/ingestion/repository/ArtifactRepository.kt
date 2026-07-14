package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ArtifactRepository : JpaRepository<Artifact, UUID> {
    fun findBySourceId(sourceId: String): Artifact?

    fun findAllByIngestionRunId(runId: UUID): MutableList<Artifact>

    /**
     * Returns the earliest ingestion timestamp across all artifacts of a GitHub component.
     *
     * The component is an `owner/repo` string; artifact source ids have the form
     * `github:owner/repo:TYPE:...`, so they are matched by prefix. Because existing artifacts are
     * updated in place on re-ingestion (their `ingestedAt` is immutable), the minimum is the time
     * the component was first ingested. Returns null when the component has no ingested artifacts.
     */
    @Query(
        "SELECT MIN(a.ingestedAt) FROM Artifact a WHERE a.sourceId LIKE CONCAT('github:', :component, ':%')",
    )
    fun findFirstIngestedAt(
        @Param("component") component: String,
    ): Instant?
}
