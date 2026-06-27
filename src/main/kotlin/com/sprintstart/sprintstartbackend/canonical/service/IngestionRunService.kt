package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.IngestionRunResponse
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class IngestionRunService(
    private val ingestionRunRepository : IngestionRunRepository,
) {


    fun getRecentRuns(
        limit : Int = 10,
    ): List<IngestionRunResponse> =
        ingestionRunRepository
            .findByOrderByStartedAtDesc(
                PageRequest.of(0, limit))
            .map {
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