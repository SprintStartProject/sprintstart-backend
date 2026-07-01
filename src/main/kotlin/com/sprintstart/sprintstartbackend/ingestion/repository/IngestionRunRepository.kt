package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface IngestionRunRepository : JpaRepository<IngestionRun, UUID> {
    fun findAllByStatus(status: IngestionRunStatus): List<IngestionRun>

    fun findByOrderByStartedAtDesc(
        pageable: Pageable,
    ): List<IngestionRun>

    fun findFirstByOrderByStartedAtDesc(): IngestionRun?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM IngestionRun r WHERE r.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: UUID,
    ): Optional<IngestionRun>

    @Modifying
    @Query("UPDATE IngestionRun r SET r.ingestedCount = r.ingestedCount + 1 WHERE r.id = :id")
    fun incrementIngestedCount(
        @Param("id") id: UUID,
    )

    @Modifying
    @Query("UPDATE IngestionRun r SET r.updatedCount = r.updatedCount + 1 WHERE r.id = :id")
    fun incrementUpdatedCount(
        @Param("id") id: UUID,
    )
}
