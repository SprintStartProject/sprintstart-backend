package com.sprintstart.sprintstartbackend.user.repository

import com.sprintstart.sprintstartbackend.user.model.entity.DashboardLayout
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.UUID

interface DashboardLayoutRepository : JpaRepository<DashboardLayout, UUID> {
    fun findByUserId(userId: UUID): DashboardLayout?

    @Modifying
    fun deleteByUserId(userId: UUID)

    /**
     * Writes a user's dashboard arrangement, whether or not they already have one.
     *
     * One statement rather than "look, then insert or update", for the same reason as the board's
     * arrangement: two tabs saving a first arrangement must resolve as last-write-wins, not as a
     * constraint violation. [id] is only used when this inserts.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO dashboard_layouts (id, user_id, version, payload, created_at, updated_at)
            VALUES (:id, :userId, :version, :payload, :now, :now)
            ON CONFLICT (user_id) DO UPDATE
            SET version = EXCLUDED.version, payload = EXCLUDED.payload, updated_at = EXCLUDED.updated_at
        """,
        nativeQuery = true,
    )
    fun upsert(
        @Param("id") id: UUID,
        @Param("userId") userId: UUID,
        @Param("version") version: Int,
        @Param("payload") payload: String,
        @Param("now") now: Instant,
    )
}
