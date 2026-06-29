package com.sprintstart.sprintstartbackend.canonical.controller

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.ArtifactPageResponse
import com.sprintstart.sprintstartbackend.canonical.service.ArtifactQueryService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

private const val DEFAULT_PAGE = "1"
private const val DEFAULT_SIZE = "20"
private const val MAX_PAGE_SIZE = 100L

@RestController
@Validated
@RequestMapping("/api/v1/")
@Tag(
    name = "Artifacts",
    description = "",
)
class ArtifactController(
    private val artifactQueryService: ArtifactQueryService,
) {
    @GetMapping("admin/artifacts")
    fun getAllArtifacts(
        @RequestParam(defaultValue = DEFAULT_PAGE) @Min(1) page: Int,
        @RequestParam(defaultValue = DEFAULT_SIZE) @Min(1) @Max(MAX_PAGE_SIZE) size: Int,
        @RequestParam(defaultValue = "") filter: String,
    ): ResponseEntity<ArtifactPageResponse> =
        ResponseEntity.ok(
            artifactQueryService.getAllArtifacts(page, size, filter),
        )
}
