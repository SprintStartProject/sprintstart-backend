package com.sprintstart.sprintstartbackend.offboarding.controller

import com.sprintstart.sprintstartbackend.offboarding.model.RiskSnapshotId
import com.sprintstart.sprintstartbackend.offboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskParameters
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskComparisonResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSnapshotResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSnapshotSummaryResponse
import com.sprintstart.sprintstartbackend.offboarding.service.RiskSnapshotService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

private const val DEFAULT_MOVEMENTS = "100"
private const val MOST_MOVEMENTS = 1000

/**
 * PM-only store of the risk maps a project's managers computed, and the comparison of two of them.
 *
 * A stored map keeps the ranking as it was served, whatever the history, the identity links or the settings
 * look like later. Ranking the same departure under different settings and comparing the maps is how the
 * settings are judged.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/offboarding/risk/snapshots")
@Tag(name = "Offboarding", description = "Knowledge at risk when people leave a project")
class OffboardingSnapshotController(
    private val riskSnapshotService: RiskSnapshotService,
) {
    /**
     * Computes a departure's risk map and stores it.
     */
    @Operation(
        summary = "Compute a risk map and store it",
        description = "Takes the same parameters as the risk map, plus an optional label, ranks the departure and " +
            "stores the result with its settings, date and moment of computation. PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Risk map computed and stored"),
            ApiResponse(responseCode = "400", description = "Nobody named, a blank person or an invalid setting"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    fun create(
        @PathVariable projectId: ProjectId,
        parameters: RiskParameters,
        @RequestParam(required = false) label: String?,
    ): RiskSnapshotResponse =
        riskSnapshotService.create(projectId, parameters.toRequest(), label).toResponse(parameters.listLimit)

    /**
     * Lists the stored risk maps.
     */
    @Operation(
        summary = "List a project's stored risk maps",
        description = "Newest first, without their rankings. PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Stored risk maps returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
        ],
    )
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    fun list(
        @PathVariable projectId: ProjectId,
    ): List<RiskSnapshotSummaryResponse> = riskSnapshotService.list(projectId).map { it.toResponse() }

    /**
     * Compares the rankings of two stored risk maps.
     */
    @Operation(
        summary = "Compare two stored risk maps",
        description = "Reports how many files only one ranking lists, the overlap of their top files, the rank " +
            "correlation of the shared files and the files that moved most. PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Comparison returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
            ApiResponse(responseCode = "404", description = "A risk map does not exist in this project"),
        ],
    )
    @GetMapping("/compare")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    fun compare(
        @PathVariable projectId: ProjectId,
        @RequestParam left: UUID,
        @RequestParam right: UUID,
        @RequestParam(defaultValue = DEFAULT_MOVEMENTS) limit: Int,
    ): RiskComparisonResponse =
        riskSnapshotService
            .compare(projectId, RiskSnapshotId(left), RiskSnapshotId(right))
            .toResponse(limit.coerceIn(1, MOST_MOVEMENTS))

    /**
     * Returns one stored risk map.
     */
    @Operation(
        summary = "Read a stored risk map",
        description = "The ranking as it was served when it was computed. PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Risk map returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
            ApiResponse(responseCode = "404", description = "No such risk map in this project"),
        ],
    )
    @GetMapping("/{snapshot}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    fun read(
        @PathVariable projectId: ProjectId,
        @PathVariable snapshot: RiskSnapshotId,
        @RequestParam(defaultValue = DEFAULT_MOVEMENTS) limit: Int,
    ): RiskSnapshotResponse = riskSnapshotService.get(projectId, snapshot).toResponse(limit.coerceIn(1, MOST_MOVEMENTS))

    /**
     * Throws a stored risk map away.
     */
    @Operation(
        summary = "Delete a stored risk map",
        description = "PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Risk map deleted"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
            ApiResponse(responseCode = "404", description = "No such risk map in this project"),
        ],
    )
    @DeleteMapping("/{snapshot}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    fun delete(
        @PathVariable projectId: ProjectId,
        @PathVariable snapshot: RiskSnapshotId,
    ) = riskSnapshotService.delete(projectId, snapshot)
}
