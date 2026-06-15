package com.sprintstart.sprintstartbackend.canonical.controller

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.IngestionRunResponse
import com.sprintstart.sprintstartbackend.canonical.service.IngestionRunService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("/api/v1/ingestion-runs")
@Tag(
    name = "Ingestion Runs",
    description = "History of ingestion runs",
)
class IngestionRunController(
    private val ingestionRunService: IngestionRunService,
) {
    @GetMapping
    @Operation(
        summary = "Get recent ingestion runs",
        description = "Returns recent ingestion runs ordered by most recent first")
    fun getRecentRuns(
        @Min(1)
        @Max(100)
        @RequestParam(defaultValue = "50") limit : Int,
    ): ResponseEntity<List<IngestionRunResponse>> =
        ResponseEntity.ok(
            ingestionRunService.getRecentRuns(limit),
        )
}
