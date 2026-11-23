package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.request.project.AssignProjectUsersRequest
import com.sprintstart.sprintstartbackend.user.model.request.project.SetProjectManagerRequest
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectDetailResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectManagerResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectUserResponse
import com.sprintstart.sprintstartbackend.user.service.AdminProjectService
import com.sprintstart.sprintstartbackend.user.service.ProjectManagerService
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
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

/**
 * REST API for the people on a project: its assigned users, their project-specific roles and its
 * manager.
 *
 * Project-specific user roles are returned only through these project-scoped responses.
 */
@Tag(
    name = "Admin Projects",
    description = "Administrative endpoints for managing project user assignments and project managers.",
)
@RestController
@RequestMapping("/api/v1/admin/projects")
class AdminProjectMembersController(
    private val adminProjectService: AdminProjectService,
    private val projectManagerService: ProjectManagerService,
) {
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
     * Existing assignments are left unchanged. A regular user belongs to exactly one project, so
     * assigning one moves them: their memberships in other projects, including the project roles held
     * there, are removed, and their onboarding path is reset. Users with the PM or ADMIN role keep
     * their other memberships.
     *
     * @param projectId Project identifier.
     * @param request User assignment payload.
     * @return The full assigned-user list after the operation.
     */
    @Operation(
        summary = "Assign project users",
        description = "Assigns one or more users to a project. Regular users are moved: their memberships " +
            "in other projects, the project roles held there and their onboarding path are removed. " +
            "Users with the PM or ADMIN role keep their other memberships.",
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
        @SwaggerRequestBody(
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

    /**
     * Assigns the project manager responsible for a project.
     *
     * A project has at most one manager, so an existing assignment is replaced. Only administrators
     * may change manager assignments; project managers cannot reassign themselves or others.
     *
     * @param projectId Project identifier.
     * @param request Manager assignment payload.
     * @return The updated project.
     */
    @Operation(
        summary = "Set project manager",
        description = "Assigns the single project manager of a project, replacing any existing assignment.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Project manager assigned successfully"),
            ApiResponse(responseCode = "400", description = "User does not hold the PM or ADMIN role"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to assign project managers"),
            ApiResponse(responseCode = "404", description = "Project or user not found"),
        ],
    )
    @PutMapping("/{projectId}/manager")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun setManager(
        @Parameter(description = "UUID of the project")
        @PathVariable projectId: UUID,
        @SwaggerRequestBody(
            description = "Identifier of the user to assign as project manager.",
            required = true,
            content = [Content(schema = Schema(implementation = SetProjectManagerRequest::class))],
        )
        @Valid
        @RequestBody
        request: SetProjectManagerRequest,
    ): AdminProjectDetailResponse {
        return projectManagerService.setManager(projectId, request)
    }

    /**
     * Removes the manager assignment from a project.
     *
     * @param projectId Project identifier.
     */
    @Operation(
        summary = "Clear project manager",
        description = "Removes the manager assignment from a project without changing member assignments.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Project manager cleared successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to clear project managers"),
            ApiResponse(responseCode = "404", description = "Project not found"),
        ],
    )
    @DeleteMapping("/{projectId}/manager")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    fun clearManager(
        @Parameter(description = "UUID of the project")
        @PathVariable projectId: UUID,
    ) {
        projectManagerService.clearManager(projectId)
    }

    /**
     * Returns all users eligible to be assigned as a project manager.
     *
     * Mapped below a literal `candidates` segment so the path cannot be confused with the
     * `GET /{id}` project lookup.
     *
     * @return Users holding the PM or ADMIN role.
     */
    @Operation(
        summary = "Get project manager candidates",
        description = "Returns all users that may be assigned as a project manager.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Candidates returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to list candidates"),
        ],
    )
    @GetMapping("/candidates/managers")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun getManagerCandidates(): List<ProjectManagerResponse> {
        return projectManagerService.getManagerCandidates()
    }
}
