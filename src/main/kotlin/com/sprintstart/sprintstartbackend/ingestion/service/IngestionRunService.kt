package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.IngestionRunResponse
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service

/**
 * Reads recent ingestion runs for API consumers.
 *
 * This service keeps pagination and response mapping out of the controller so the API surface can
 * stay stable even if the persistence model grows additional run metadata later.
 */
@Service
class IngestionRunService(
    private val ingestionRunRepository: IngestionRunRepository,
) {
    /**
     * Returns the newest ingestion runs first.
     *
     * @param limit maximum number of runs returned from the first page of run history
     * @return API-ready run summaries including counters and failed items
     * @throws IllegalArgumentException If Spring Data rejects the requested page size.
     */
    fun getRecentRuns(
        limit: Int = 10,
    ): List<IngestionRunResponse> =
        ingestionRunRepository
            .findByOrderByStartedAtDesc(
                PageRequest.of(0, limit),
            ).map {
                IngestionRunResponse(
                    it.id,
                    sourceSystem = it.sourceSystem,
                    startedAt = it.startedAt,
                    finishedAt = it.finishedAt,
                    ingestedCount = it.ingestedCount,
                    updatedCount = it.updatedCount,
                    failedCount = it.failedCount,
                    failedItems = it.failedItems,
                )
            }
}
