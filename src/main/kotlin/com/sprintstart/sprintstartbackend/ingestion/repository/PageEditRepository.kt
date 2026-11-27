package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.PageEditReading
import com.sprintstart.sprintstartbackend.ingestion.model.entity.PageEdit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface PageEditRepository : JpaRepository<PageEdit, UUID> {
    /**
     * Drops every version recorded for one page artifact.
     *
     * @return The number of removed rows.
     */
    @Modifying
    @Query("DELETE FROM PageEdit e WHERE e.artifact.id = :artifactId")
    fun deleteAllByArtifactId(
        @Param("artifactId") artifactId: UUID,
    ): Int

    /**
     * Reads the versions of every page linked to a project, up to a moment, oldest first, under the
     * pages' current titles.
     */
    @Query(
        """
            SELECT new com.sprintstart.sprintstartbackend.ingestion.external.model.dto.PageEditReading(
                e.repository, COALESCE(a.title, a.sourceId), e.authorAccountId, e.editedAt)
            FROM PageEdit e
            JOIN e.artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
                AND e.editedAt <= :asOf
            ORDER BY e.editedAt, e.id
        """,
    )
    fun findEdits(
        @Param("projectId") projectId: UUID,
        @Param("asOf") asOf: Instant,
    ): List<PageEditReading>
}
