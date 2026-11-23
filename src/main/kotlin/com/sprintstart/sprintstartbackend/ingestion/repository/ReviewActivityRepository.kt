package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ReviewActivity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface ReviewActivityRepository : JpaRepository<ReviewActivity, UUID> {
    /**
     * Drops every review recorded for one pull request artifact.
     *
     * Written as a bulk delete so that re-recording a pull request replaces its reviews without
     * loading them first.
     *
     * @param artifactId The pull request artifact whose reviews should go.
     * @return The number of removed rows.
     */
    @Modifying
    @Query("DELETE FROM ReviewActivity r WHERE r.artifact.id = :artifactId")
    fun deleteAllByArtifactId(
        @Param("artifactId") artifactId: UUID,
    ): Int

    /**
     * Reads the reviews of every pull request linked to a project, up to a moment, oldest first.
     *
     * Selected as the exported value type rather than as entities, so reading many reviews loads
     * neither the pull request artifacts nor their content.
     *
     * @param projectId The project whose pull requests are read.
     * @param asOf Reviews submitted after this moment are left out.
     */
    @Query(
        """
            SELECT new com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview(
                r.repository, r.path, r.reviewerLogin, r.reviewedAt)
            FROM ReviewActivity r
            JOIN r.artifact a
            JOIN a.projectIdsInternal p
            WHERE p = :projectId
                AND r.reviewedAt <= :asOf
            ORDER BY r.reviewedAt, r.id
        """,
    )
    fun findReviews(
        @Param("projectId") projectId: UUID,
        @Param("asOf") asOf: Instant,
    ): List<FileReview>
}
