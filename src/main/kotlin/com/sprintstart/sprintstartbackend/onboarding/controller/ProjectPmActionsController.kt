package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.response.path.ProjectPmActionsResponse
import com.sprintstart.sprintstartbackend.onboarding.service.ProjectPmActionsService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/onboarding/projects/{projectId}/pm-actions")
@Tag(name = "Onboarding - PM Actions", description = "Pending skip requests and unread onboarding feedback")
class ProjectPmActionsController(
    private val projectPmActionsService: ProjectPmActionsService,
) {
    /**
     * Returns the actionable-item count for a project's manager or an administrator.
     *
     * Each pending skip request and unread feedback item from current project members contributes
     * to the total. A member can contribute multiple items, regardless of their active step.
     */
    @Operation(
        summary = "Count pending PM actions for a project",
        description = "Returns pending skip requests, unread onboarding feedback and their total for current " +
            "project members. Includes all onboarding steps and feedback without a step. Only the assigned " +
            "project manager or an administrator may read these counts.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "PM action counts returned",
                content = [Content(schema = Schema(implementation = ProjectPmActionsResponse::class))],
            ),
            ApiResponse(responseCode = "400", description = "Invalid project identifier"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Caller may not manage this project"),
            ApiResponse(responseCode = "404", description = "Project does not exist"),
        ],
    )
    @GetMapping
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
    fun getPmActions(@PathVariable projectId: UUID): ProjectPmActionsResponse {
        return projectPmActionsService.getPmActions(projectId)
    }
}
