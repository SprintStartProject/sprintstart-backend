package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileComplexity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface FileComplexityRepository : JpaRepository<FileComplexity, UUID> {
    /** The measurement of one file, or `null` when it was never measured. */
    fun findByRepositoryAndPath(repository: String, path: String): FileComplexity?

    /**
     * Drops the measurement of one file.
     *
     * @return The number of removed rows.
     */
    @Modifying
    @Query("DELETE FROM FileComplexity c WHERE c.repository = :repository AND c.path = :path")
    fun deleteByFile(
        @Param("repository") repository: String,
        @Param("path") path: String,
    ): Int

    /** The measurements of every file of the given repositories, by repository and path. */
    @Query("SELECT c FROM FileComplexity c WHERE c.repository IN :repositories ORDER BY c.repository, c.path")
    fun readAll(
        @Param("repositories") repositories: Collection<String>,
    ): List<FileComplexity>
}
