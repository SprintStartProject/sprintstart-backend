package com.sprintstart.sprintstartbackend.offboarding.controller

import com.sprintstart.sprintstartbackend.offboarding.model.response.StudyExportResponse
import com.sprintstart.sprintstartbackend.offboarding.service.StudyExportService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * PM-only export of a project's stored risk maps for study.
 *
 * Study data carries pseudonyms, never emails or logins (requirement QR-1).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/offboarding/export")
@Tag(name = "Offboarding", description = "Knowledge at risk when people leave a project")
class OffboardingExportController(
    private val studyExportService: StudyExportService,
) {
    /**
     * Exports every stored risk map of the project with its people pseudonymised.
     */
    @Operation(
        summary = "Export the stored risk maps with pseudonymised people",
        description = "Every stored map, oldest first, with the leaving people and the people who stay on a file " +
            "replaced by pseudonyms. The same person has the same pseudonym throughout the project's export and a " +
            "different one in another project. Labels are left out. PM/Admin of the project only.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Export returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Not a manager of this project"),
        ],
    )
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM') and @projectAuth.canManageProject(authentication, #projectId)")
    fun export(
        @PathVariable projectId: ProjectId,
    ): StudyExportResponse = studyExportService.export(projectId)
}
