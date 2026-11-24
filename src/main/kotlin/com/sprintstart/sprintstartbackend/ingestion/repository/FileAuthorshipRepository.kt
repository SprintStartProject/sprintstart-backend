package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FileAuthorship
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface FileAuthorshipRepository : JpaRepository<FileAuthorship, UUID> {
    /**
     * Drops the authorship of one file.
     *
     * @return The number of removed rows.
     */
    @Modifying
    @Query("DELETE FROM FileAuthorship a WHERE a.repository = :repository AND a.path = :path")
    fun deleteByFile(
        @Param("repository") repository: String,
        @Param("path") path: String,
    ): Int

    /** The authorship of every file of the given repositories, as the exported value type. */
    @Query(
        """
            SELECT new com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileAuthorshipReading(
                a.repository, a.path, a.authorEmail, a.lines)
            FROM FileAuthorship a
            WHERE a.repository IN :repositories
            ORDER BY a.repository, a.path, a.authorEmail
        """,
    )
    fun readAll(
        @Param("repositories") repositories: Collection<String>,
    ): List<FileAuthorshipReading>
}
