package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

/**
 * Persistence access for stored artifacts and project-scoped artifact searches.
 */
interface ArtifactRepository : JpaRepository<Artifact, UUID> {
    fun findBySourceId(sourceId: String): Artifact?

    fun findAllByIngestionRunId(runId: UUID): MutableList<Artifact>

    /**
     * Returns one artifact page limited to artifacts linked to the given project.
     */
    @Query(
        """
            SELECT DISTINCT a
            FROM Artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
        """,
    )
    fun findAllByProjectId(@Param("projectId") projectId: UUID, pageable: Pageable): Page<Artifact>

    /**
     * Returns one filtered artifact page limited to artifacts linked to the given project.
     */
    @Query(
        """
            SELECT DISTINCT a
            FROM Artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
                AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.artifactType) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.sourceSystem) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.metadata) LIKE LOWER(CONCAT('%', :filter, '%')))
        """,
    )
    fun searchByProjectId(
        @Param(
            "projectId",
        ) projectId: UUID,
        @Param("filter") filter: String, pageable: Pageable,
    ): Page<Artifact>

    fun deleteBySourceId(sourceId: String)

    /**
     * Returns one filtered artifact page across all projects.
     */
    @Query(
        """
            SELECT a
            FROM Artifact a
            WHERE LOWER(a.title) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.artifactType) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.sourceSystem) LIKE LOWER(CONCAT('%', :filter, '%'))
                OR LOWER(a.metadata) LIKE LOWER(CONCAT('%', :filter, '%'))
        """,
    )
    fun search(
        @Param("filter") filter: String, pageable: Pageable,
    ): Page<Artifact>

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
