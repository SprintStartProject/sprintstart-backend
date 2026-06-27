package com.sprintstart.sprintstartbackend.canonical.controller

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.SourceIngestionStatusResponse
import com.sprintstart.sprintstartbackend.canonical.service.IngestionStatusService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Read-only HTTP entry point for the latest ingestion summary per source.
 *
 * The response is intentionally compact so admin views can render source health without loading
 * the full run history first.
 */
@RestController
@RequestMapping("/api/v1/ingestion-status")
@Tag(
    name = "Ingestion status",
    description = "Latest ingestion health summary grouped by source system",
)
class IngestionStatusController(
    private val ingestionStatusService: IngestionStatusService,
) {
    @GetMapping
    @Operation(
        summary = "Get latest ingestion status per source",
        description =
            "Returns one status row per exposed source system using the latest known run, " +
                "including aggregate counters and recorded failed items.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Per-source ingestion status returned successfully"),
        ],
    )
    fun getIngestionStatusPerSource(): ResponseEntity<List<SourceIngestionStatusResponse>> =
        ResponseEntity.ok(
            ingestionStatusService.getIngestionStatusPerSource(),
        )
}
