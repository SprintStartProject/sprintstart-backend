package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IssueActivityReading
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IssueActivity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface IssueActivityRepository : JpaRepository<IssueActivity, UUID> {
    /**
     * Drops every activity recorded for one issue artifact.
     *
     * @return The number of removed rows.
     */
    @Modifying
    @Query("DELETE FROM IssueActivity i WHERE i.artifact.id = :artifactId")
    fun deleteAllByArtifactId(
        @Param("artifactId") artifactId: UUID,
    ): Int

    /** The activities on every ticket linked to a project, up to a moment, oldest first. */
    @Query(
        """
            SELECT new com.sprintstart.sprintstartbackend.ingestion.external.model.dto.IssueActivityReading(
                i.issueKey, i.accountId, i.displayName, i.at)
            FROM IssueActivity i
            JOIN i.artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
                AND i.at <= :asOf
            ORDER BY i.at, i.id
        """,
    )
    fun findActivities(
        @Param("projectId") projectId: UUID,
        @Param("asOf") asOf: Instant,
    ): List<IssueActivityReading>
}
