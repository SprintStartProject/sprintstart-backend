package com.sprintstart.sprintstartbackend.canonical.controller

import com.sprintstart.sprintstartbackend.canonical.model.dto.response.ArtifactPageResponse
import com.sprintstart.sprintstartbackend.canonical.service.ArtifactQueryService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


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
        @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam filter: String,
    ): ResponseEntity<ArtifactPageResponse> =
        ResponseEntity.ok(
            artifactQueryService.getAllArtifacts(page, size, filter),
        )

    @GetMapping("projects/{projectId}/artifacts")
    fun getProjectArtifacts(
        @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam filter: String,
        @PathVariable projectId: String
    ): ResponseEntity<ArtifactPageResponse> =
        ResponseEntity.ok(
            artifactQueryService.getProjectArtifacts(page, size, filter, projectId),
        )

}
