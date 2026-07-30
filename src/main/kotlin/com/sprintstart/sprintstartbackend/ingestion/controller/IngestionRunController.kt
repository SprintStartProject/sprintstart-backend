package com.sprintstart.sprintstartbackend.ingestion.controller

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.IngestionRunPageResponse
import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.IngestionRunResponse
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionRunService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

private const val MIN_LIMIT: Long = 1
private const val MAX_LIMIT: Long = 100
private const val DEFAULT_LIMIT = "50"
private const val DEFAULT_PAGE = "1"
private const val DEFAULT_PAGE_SIZE = "20"
private const val MAX_PAGE_SIZE: Long = 100

/**
 * Read-only HTTP entry point for browsing ingestion executions.
 *
 * Besides the compact recent-runs list, this endpoint exposes a single run for run-drawer deep
 * links and a filtered, paginated run history for per-repository views.
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

    @GetMapping("/page")
    @Operation(
        summary = "Get a filtered page of ingestion runs",
        description =
            "Returns a filtered, paginated page of ingestion runs ordered by newest first. All " +
                "filters are optional and combined with AND semantics; projectId is resolved to the " +
                "repositories connected to that project.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ingestion runs page returned successfully"),
            ApiResponse(responseCode = "400", description = "A pagination or filter parameter is invalid"),
        ],
    )
    @Suppress("LongParameterList")
    fun getRunsPage(
        @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) page: Int,
        @RequestParam(defaultValue = DEFAULT_PAGE_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
        @RequestParam(required = false) sourceSystem: SourceSystem?,
        @RequestParam(required = false) repositoryId: UUID?,
        @RequestParam(required = false) projectId: UUID?,
        @RequestParam(required = false) status: IngestionRunStatus?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        since: Instant?,
    ): ResponseEntity<IngestionRunPageResponse> =
        ResponseEntity.ok(
            ingestionRunService.getRuns(page, size, sourceSystem, repositoryId, projectId, status, since),
        )

    @GetMapping("/{runId}")
    @Operation(
        summary = "Get a single ingestion run",
        description =
            "Returns one ingestion run by id, including its full source-instance metadata, counters, " +
                "and failed items. Used by the run drawer and deep links.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Ingestion run returned successfully"),
            ApiResponse(responseCode = "404", description = "No ingestion run exists for the given id"),
        ],
    )
    fun getRun(
        @PathVariable runId: UUID,
    ): ResponseEntity<IngestionRunResponse> =
        ResponseEntity.ok(
            ingestionRunService.getRun(runId),
        )
}
