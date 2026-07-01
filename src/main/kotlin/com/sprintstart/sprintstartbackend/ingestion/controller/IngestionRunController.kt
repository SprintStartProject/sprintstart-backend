package com.sprintstart.sprintstartbackend.ingestion.controller

import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.IngestionRunResponse
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val MIN_LIMIT: Long = 1
private const val MAX_LIMIT: Long = 100
private const val DEFAULT_LIMIT = "50"

/**
 * Read-only HTTP entry point for browsing recent ingestion executions.
 *
 * This endpoint is intended for operational views that need a compact run history rather than
 * artifact-level details.
 */
@RestController
@Validated
@RequestMapping("/api/v1/ingestion-runs")
@Tag(
    name = "Ingestion Runs",
    description = "Read-only history of ingestion runs with per-run counters and timing",
)
class IngestionRunController(
    private val ingestionRunService: IngestionRunService,
) {
    @GetMapping
    @Operation(
        summary = "Get recent ingestion runs",
        description =
            "Returns recent ingestion runs ordered by newest first. " +
                "Each row includes source, timestamps, aggregate counters, and failed items recorded for that run.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Recent ingestion runs returned successfully"),
            ApiResponse(responseCode = "400", description = "The limit query parameter is outside the allowed range"),
        ],
    )
    fun getRecentRuns(
        @Min(MIN_LIMIT)
        @Max(MAX_LIMIT)
        @RequestParam(defaultValue = DEFAULT_LIMIT)
        limit: Int,
    ): ResponseEntity<List<IngestionRunResponse>> =
        ResponseEntity.ok(
            ingestionRunService.getRecentRuns(limit),
        )
}
