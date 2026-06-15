package com.sprintstart.sprintstartbackend.canonical.service

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.IngestionRunResponse
import com.sprintstart.sprintstartbackend.canonical.repository.IngestionRunRepository
import org.springframework.stereotype.Service

@Service
class IngestionRunService(
    private val ingestionRunRepository : IngestionRunRepository,
) {

    fun getRecentRuns(
        limit : Int,
    ): List<IngestionRunResponse> =
        ingestionRunRepository
            .findAll()
            .map {
                IngestionRunResponse(

                )
            }

}