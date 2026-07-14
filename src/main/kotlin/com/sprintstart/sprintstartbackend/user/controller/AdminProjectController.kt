package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.request.project.AssignProjectUsersRequest
import com.sprintstart.sprintstartbackend.user.model.request.project.CreateAdminProjectRequest
import com.sprintstart.sprintstartbackend.user.model.request.project.PatchAdminProjectRequest
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectDetailResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectListResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.DeleteProjectResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectUserResponse
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

/**
 * REST API for administrative project management.
 *
 * These endpoints expose project metadata, connected source summaries and project user
 * assignments for administrators. Project-specific user roles are returned only through
 * these project-scoped responses.
 */
@Tag(
    name = "Admin Projects",
    description = "Administrative endpoints for managing projects and project user assignments.",
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
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
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
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
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

    /**
     * Returns all users assigned to a project.
     *
     * Project-specific roles are returned in the context of the requested project.
     *
     * @param projectId Project identifier.
     * @return Users assigned to the project.
     */
    @Operation(
        summary = "Get project users",
        description = "Returns all users assigned to a project with their global and project-specific roles.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Project users returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access project users"),
            ApiResponse(responseCode = "404", description = "Project not found"),
        ],
    )
    @GetMapping("/{projectId}/users")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun getProjectUsers(
        @Parameter(description = "UUID of the project whose users should be returned")
        @PathVariable projectId: UUID,
    ): List<ProjectUserResponse> {
        return adminProjectService.getProjectUsers(projectId)
    }

    /**
     * Assigns users to a project.
     *
     * Existing assignments are left unchanged. Project-specific roles are not changed by this endpoint.
     *
     * @param projectId Project identifier.
     * @param request User assignment payload.
     * @return The full assigned-user list after the operation.
     */
    @Operation(
        summary = "Assign project users",
        description = "Assigns one or more users to a project without changing project-specific roles.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Users assigned successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to assign users"),
            ApiResponse(responseCode = "404", description = "Project or user not found"),
        ],
    )
    @PostMapping("/{projectId}/users")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun assignUsers(
        @Parameter(description = "UUID of the project receiving users")
        @PathVariable projectId: UUID,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User IDs to assign to the project.",
            required = true,
            content = [Content(schema = Schema(implementation = AssignProjectUsersRequest::class))],
        )
        @Valid
        @RequestBody
        request: AssignProjectUsersRequest,
    ): List<ProjectUserResponse> {
        return adminProjectService.assignUsers(projectId, request)
    }

    /**
     * Removes a user from a project.
     *
     * A successful removal returns `204 No Content`; missing projects or assignments return `404`.
     *
     * @param projectId Project identifier.
     * @param userId User identifier.
     */
    @Operation(
        summary = "Remove project user",
        description = "Removes a user assignment from a project and returns no response body on success.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "User removed from project successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to remove users"),
            ApiResponse(responseCode = "404", description = "Project or assignment not found"),
        ],
    )
    @DeleteMapping("/{projectId}/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    fun removeUser(
        @Parameter(description = "UUID of the project")
        @PathVariable projectId: UUID,
        @Parameter(description = "UUID of the user to remove from the project")
        @PathVariable userId: UUID,
    ) {
        adminProjectService.removeUser(projectId, userId)
    }
}
