package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileChange
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface FileChangeRepository : JpaRepository<FileChange, UUID> {
    /**
     * Drops every changed file recorded for one commit artifact.
     *
     * Written as a bulk delete so that re-recording a commit replaces its files without loading
     * them first.
     *
     * @param artifactId The commit artifact whose files should go.
     * @return The number of removed rows.
     */
    @Modifying
    @Query("DELETE FROM FileChange f WHERE f.artifact.id = :artifactId")
    fun deleteAllByArtifactId(
        @Param("artifactId") artifactId: UUID,
    ): Int

    /**
     * Reads the file changes of every commit linked to a project, up to a moment, oldest first.
     *
     * Selected as the exported value type rather than as entities, so reading a long history loads
     * neither the commit artifacts nor their content.
     *
     * @param projectId The project whose commits are read.
     * @param asOf Changes made after this moment are left out.
     */
    @Query(
        """
            SELECT new com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution(
                f.repository, f.path, f.renamedFrom, f.author.email, f.changedAt)
            FROM FileChange f
            JOIN f.artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
                AND f.changedAt <= :asOf
            ORDER BY f.changedAt, f.id
        """,
    )
    fun findContributions(
        @Param("projectId") projectId: UUID,
        @Param("asOf") asOf: Instant,
    ): List<FileContribution>
}
