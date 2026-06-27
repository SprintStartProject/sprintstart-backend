package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.SourceIngestionStatusResponse
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import org.springframework.stereotype.Service

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
     * For the current GitHub-only v0 shape, the method returns a single row. When no run exists,
     * counters default to zero and the timestamp stays null so clients can render a "no runs yet"
     * state without special-case error handling.
     */
    fun getIngestionStatusPerSource(): List<SourceIngestionStatusResponse> {
        val lastRun = ingestionRunRepository.findFirstByOrderByStartedAt()
        val github = SourceIngestionStatusResponse(
            sourceSystem = SourceSystem.GITHUB,
            lastRunTime = lastRun?.startedAt,
            ingestedCount = lastRun?.ingestedCount ?: 0,
            updatedCount = lastRun?.updatedCount ?: 0,
            failedCount = lastRun?.failedCount ?: 0,
            failedItems = lastRun?.failedItems ?: mutableListOf(),
        )

        return listOf(github) // TODO add jira etc. later
    }
}
