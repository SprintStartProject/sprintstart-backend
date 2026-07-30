package com.sprintstart.sprintstartbackend.ingestion.controller

import com.sprintstart.sprintstartbackend.ingestion.model.dto.response.SourceInstanceIngestionStatusResponse
import com.sprintstart.sprintstartbackend.ingestion.service.IngestionSourceStatusService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Read-only HTTP entry point for per-connected-source-instance ingestion health.
 *
 * Complements the aggregate `/api/v1/ingestion-status` endpoint by returning one row per connected
 * repository, so source cards can render individual health instead of a single source-system row.
 */
@RestController
@RequestMapping("/api/v1/ingestion-sources/status")
@Tag(
    name = "Ingestion source status",
    description = "Ingestion health per connected source instance",
)
class IngestionSourceStatusController(
    private val ingestionSourceStatusService: IngestionSourceStatusService,
) {
    @GetMapping
    @Operation(
        summary = "Get ingestion status per connected source instance",
        description =
            "Returns one status row per connected GitHub repository, combining its current " +
                "connection status and snapshot timestamps with the counters of its latest run. " +
                "When projectId is provided, only repositories connected to that project are returned.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Per-source-instance ingestion status returned"),
        ],
    )
    fun getStatusPerSourceInstance(
        @RequestParam(required = false) projectId: UUID?,
    ): ResponseEntity<List<SourceInstanceIngestionStatusResponse>> =
        ResponseEntity.ok(
            ingestionSourceStatusService.getStatusPerSourceInstance(projectId),
        )
}
