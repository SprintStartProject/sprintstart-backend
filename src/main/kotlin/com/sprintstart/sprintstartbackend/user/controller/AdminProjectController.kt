package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.request.project.CreateAdminProjectRequest
import com.sprintstart.sprintstartbackend.user.model.request.project.PatchAdminProjectRequest
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectDetailResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectListResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.DeleteProjectResponse
import com.sprintstart.sprintstartbackend.user.service.AdminProjectService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

/**
 * REST API for administrative project management.
 *
 * These endpoints expose project metadata and connected source summaries for administrators.
 * Who works on a project is managed by [AdminProjectMembersController].
 */
@Tag(
    name = "Admin Projects",
    description = "Administrative endpoints for managing projects.",
)
@RestController
@RequestMapping("/api/v1/admin/projects")
class AdminProjectController(
    private val adminProjectService: AdminProjectService,
) {
    /**
     * Returns all projects with source and assigned-user summaries.
     *
     * @return All projects visible to administrators.
     */
    @Operation(
        summary = "Get all projects",
        description = "Returns all projects with metadata, connected sources and assigned-user summaries.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Projects returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access projects"),
        ],
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllProjects(): List<AdminProjectListResponse> {
        return adminProjectService.getAllProjects()
    }

    /**
     * Returns detailed project information for one project.
     *
     * @param id Project identifier.
     * @return The requested project.
     */
    @Operation(
        summary = "Get project by id",
        description = "Returns project metadata, connected sources and detailed assigned-user information.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Project found"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access projects"),
            ApiResponse(responseCode = "404", description = "Project not found"),
        ],
    )
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun getProjectById(
        @Parameter(description = "UUID of the project to retrieve")
        @PathVariable id: UUID,
    ): AdminProjectDetailResponse {
        return adminProjectService.getProjectById(id)
    }

    /**
     * Creates a project with basic metadata.
     *
     * Initial user assignments are handled by `POST /api/v1/admin/projects/{projectId}/users`.
     *
     * @param request Project creation payload.
     * @return The created project.
     */
    @Operation(
        summary = "Create project",
        description = "Creates a project with its basic metadata. User assignments are handled separately.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Project created successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to create projects"),
        ],
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    fun createProject(
        @SwaggerRequestBody(
            description = "Project metadata to create.",
            required = true,
            content = [Content(schema = Schema(implementation = CreateAdminProjectRequest::class))],
        )
        @Valid
        @RequestBody
        request: CreateAdminProjectRequest,
    ): AdminProjectDetailResponse {
        return adminProjectService.createProject(request)
    }

    /**
     * Partially updates project metadata.
     *
     * This endpoint does not update user assignments.
     *
     * @param projectId Project identifier.
     * @param request Partial project update payload.
     * @return The updated project.
     */
    @Operation(
        summary = "Patch project",
        description = "Partially updates project metadata. User assignments are not changed.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Project patched successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to patch projects"),
            ApiResponse(responseCode = "404", description = "Project not found"),
        ],
    )
    @PatchMapping("/{projectId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun patchProject(
        @Parameter(description = "UUID of the project to patch")
        @PathVariable projectId: UUID,
        @SwaggerRequestBody(
            description = "Project fields to update.",
            required = true,
            content = [Content(schema = Schema(implementation = PatchAdminProjectRequest::class))],
        )
        @Valid
        @RequestBody
        request: PatchAdminProjectRequest,
    ): AdminProjectDetailResponse {
        return adminProjectService.patchProject(projectId, request)
    }

    /**
     * Deletes a project and its local user assignments.
     *
     * @param projectId Project identifier.
     * @return Deletion confirmation DTO.
     */
    @Operation(
        summary = "Delete project",
        description = "Deletes a project and removes its local user assignments.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Project deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete projects"),
            ApiResponse(responseCode = "404", description = "Project not found"),
        ],
    )
    @DeleteMapping("/{projectId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteProject(
        @Parameter(description = "UUID of the project to delete")
        @PathVariable projectId: UUID,
    ): DeleteProjectResponse {
        return adminProjectService.deleteProject(projectId)
    }
}
