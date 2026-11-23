package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileChange
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
}
