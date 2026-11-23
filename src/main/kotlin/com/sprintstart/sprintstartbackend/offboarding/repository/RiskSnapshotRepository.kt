package com.sprintstart.sprintstartbackend.offboarding.repository

import com.sprintstart.sprintstartbackend.offboarding.model.SnapshotRow
import com.sprintstart.sprintstartbackend.offboarding.model.entity.RiskSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface RiskSnapshotRepository : JpaRepository<RiskSnapshot, UUID> {
    /**
     * Lists a project's stored risk maps, newest first, without their rankings.
     *
     * @param projectId The project whose maps are listed.
     */
    @Query(
        """
            SELECT new com.sprintstart.sprintstartbackend.offboarding.model.SnapshotRow(
                s.id, s.label, s.times.computedAt, s.times.asOf, s.departing, s.profileName,
                s.counts.files, s.counts.orphaned, s.counts.hoarded)
            FROM RiskSnapshot s
            WHERE s.projectId = :projectId
            ORDER BY s.times.computedAt DESC, s.id
        """,
    )
    fun findSummaries(
        @Param("projectId") projectId: UUID,
    ): List<SnapshotRow>

    /**
     * Reads one stored risk map, only when it belongs to the project.
     *
     * @param projectId The project the map must belong to.
     * @param snapshotId The map to read.
     */
    @Query("SELECT s FROM RiskSnapshot s WHERE s.projectId = :projectId AND s.id = :snapshotId")
    fun findInProject(
        @Param("projectId") projectId: UUID,
        @Param("snapshotId") snapshotId: UUID,
    ): RiskSnapshot?

    /**
     * Deletes one stored risk map, only when it belongs to the project.
     *
     * @return The number of removed rows, 0 when the project has no such map.
     */
    @Modifying
    @Query("DELETE FROM RiskSnapshot s WHERE s.projectId = :projectId AND s.id = :snapshotId")
    fun deleteInProject(
        @Param("projectId") projectId: UUID,
        @Param("snapshotId") snapshotId: UUID,
    ): Int
}
