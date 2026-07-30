package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface IngestionRunRepository :
    JpaRepository<IngestionRun, UUID>,
    JpaSpecificationExecutor<IngestionRun> {
    fun findByOrderByStartedAtDesc(
        pageable: Pageable,
    ): List<IngestionRun>

    fun findFirstByOrderByStartedAtDesc(): IngestionRun?

    /**
     * Latest run for a specific source instance, used to attach up-to-date counters to the
     * per-source-instance status view.
     */
    fun findFirstBySourceInstanceIdOrderByStartedAtDesc(sourceInstanceId: UUID): IngestionRun?

    /**
     * Loads a run with a database write lock for lifecycle paths that mutate counters or
     * collection-valued fields from independently delivered events.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM IngestionRun r WHERE r.id = :id")
    fun findByIdForUpdate(
        @Param("id") id: UUID,
    ): Optional<IngestionRun>

    @EntityGraph(attributePaths = ["artifactIdsToDeindex"])
    @Query("SELECT r FROM IngestionRun r WHERE r.id = :id")
    fun findWithArtifactIdsToDeindexById(
        @Param("id") id: UUID,
    ): Optional<IngestionRun>
}
