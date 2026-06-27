package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.SourceIngestionStatusResponse
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import org.springframework.stereotype.Service

@Service
class IngestionStatusService(
    private val ingestionRunRepository: IngestionRunRepository,
) {
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
