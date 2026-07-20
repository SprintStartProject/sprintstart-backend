package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.SourceIngestionStatusResponse
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Builds the compact "latest status per source" view used by operational UIs.
 *
 * Unlike run history, this service collapses the persistence model down to the latest known run
 * for each exposed source system and also defines the empty-state behavior when a source has never
 * run.
 */
@Service
class IngestionStatusService(
    private val ingestionRunRepository: IngestionRunRepository,
) {
    /**
     * Returns the latest status row for each source currently exposed by the API.
     *
     * The current API shape still exposes a single GitHub row. When no run exists, counters
     * default to zero and the timestamp stays null so clients can render a "no runs yet" state
     * without special-case error handling.
     *
     * @return The latest exposed source status rows, including the empty-state row when no run
     * exists yet.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving ingestion status")
    fun getIngestionStatusPerSource(): List<SourceIngestionStatusResponse> {
        val lastRun = ingestionRunRepository.findFirstByOrderByStartedAtDesc()
        val github = SourceIngestionStatusResponse(
            sourceSystem = SourceSystem.GITHUB,
            lastRunTime = lastRun?.startedAt,
            ingestedCount = lastRun?.ingestedCount ?: 0,
            updatedCount = lastRun?.updatedCount ?: 0,
            failedCount = lastRun?.failedCount ?: 0,
            failedItems = lastRun?.failedItems ?: mutableListOf(),
            status = lastRun?.status,
        )

        return listOf(github)
    }
}
