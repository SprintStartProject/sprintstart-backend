package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ReviewActivity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
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
}
