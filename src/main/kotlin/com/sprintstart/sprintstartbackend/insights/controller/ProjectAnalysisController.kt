package com.sprintstart.sprintstartbackend.insights.controller

import com.sprintstart.sprintstartbackend.insights.model.dto.request.SaveProjectAnalysisRunRequest
import com.sprintstart.sprintstartbackend.insights.model.dto.response.ProjectAnalysisRunResponse
import com.sprintstart.sprintstartbackend.insights.service.ProjectAnalysisRunService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * The history of a project's analyses from the PM area.
 *
 * PM/Admin only, and only for projects the caller can access — the same rule as the knowledge
 * gaps, whose results an analysis includes.
 */
@RestController
@RequestMapping("/api/v1/insights/project-analysis")
@Tag(name = "Insights - Project analysis", description = "Finished project analyses, for the score history")
class ProjectAnalysisController(
    private val projectAnalysisRunService: ProjectAnalysisRunService,
) {
    /**
     * Returns the newest analyses of a project, newest first.
     *
     * @param projectId The project whose analyses to return.
     * @param limit How many runs to return, clamped to 1–50.
     * @return The runs, possibly none.
     */
    @Operation(
        summary = "Get project analysis history",
        description = "Returns the newest finished analyses of a project, newest first, with their findings. " +
            "An empty list means the project was never analysed. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Runs returned, possibly none"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role or no access to the project"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/runs")
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    fun getRuns(
        @RequestParam projectId: UUID,
        @RequestParam(defaultValue = "10") limit: Int,
    ): List<ProjectAnalysisRunResponse> {
        return projectAnalysisRunService.list(projectId, limit)
    }

    /**
     * Stores a finished analysis of a project.
     *
     * @param projectId The project that was analysed.
     * @param request The score, findings and checks of the run.
     * @return The stored run with its server-derived counts.
     */
    @Operation(
        summary = "Store a project analysis",
        description = "Records one finished analysis. The per-severity counts and the number of failed " +
            "checks are derived from the findings and checks sent. A run with a failed check must not " +
            "carry a score. Only the newest 100 runs of a project are kept. PM/Admin only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Run stored"),
            ApiResponse(responseCode = "400", description = "Invalid run, or a score despite a failed check"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role or no access to the project"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/runs")
    @PreAuthorize(
        "hasAnyRole('ADMIN', 'PM') and @projectAuth.canAccessProject(authentication, #projectId)",
    )
    fun saveRun(
        @RequestParam projectId: UUID,
        @RequestBody @Valid request: SaveProjectAnalysisRunRequest,
    ): ProjectAnalysisRunResponse {
        return projectAnalysisRunService.save(projectId, request)
    }
}
