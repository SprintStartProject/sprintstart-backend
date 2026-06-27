package com.sprintstart.sprintstartbackend.canonical.controller

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.SourceIngestionStatusResponse
import com.sprintstart.sprintstartbackend.canonical.service.IngestionStatusService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/ingestion-status")
@Tag(
    name = "Ingestion status",
)
class IngestionStatusController(
    private val ingestionStatusService: IngestionStatusService,
) {
    @GetMapping
    @Operation(summary = "Get latest ingestion status per source")
    fun getIngestionStatusPerSource(): ResponseEntity<List<SourceIngestionStatusResponse>> =
        ResponseEntity.ok(
            ingestionStatusService.getIngestionStatusPerSource(),
        )
}
