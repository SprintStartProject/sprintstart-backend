package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.request.project.AssignProjectUsersRequest
import com.sprintstart.sprintstartbackend.user.model.response.project.AdminProjectDetailResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ManagedProjectResponse
import com.sprintstart.sprintstartbackend.user.model.response.project.ProjectUserResponse
import com.sprintstart.sprintstartbackend.user.security.ProjectAuthorization
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
import org.springframework.security.core.Authentication
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

/**
 * REST API for project-manager-scoped project management.
 *
 * Project managers use these endpoints to map users to the projects they are responsible for.
 * Access is decided per project by [ProjectAuthorization]: a manager may only act on projects they
 * are assigned to, while administrators may act on any project. Project lifecycle operations
 * (create, delete) and manager assignment stay administrator-only and live on
 * [AdminProjectController].
 */
@Tag(
    name = "Projects",
    description = "Project-scoped endpoints for managing user assignments of a managed project.",
)
@RestController
@RequestMapping("/api/v1/projects")
class ProjectController(
    private val adminProjectService: AdminProjectService,
    private val projectManagerService: ProjectManagerService,
    private val projectAuth: ProjectAuthorization,
) {
    /**
     * Returns the projects the requesting user may manage.
     *
     * Administrators receive all projects; project managers receive only the projects they are the
     * assigned manager of.
     *
     * @param jwt Authenticated principal.
     * @return The manageable projects.
     */
    @Operation(
        summary = "Get manageable projects",
        description = "Returns all projects for admins and the assigned projects for project managers.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Projects returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to manage projects"),
        ],
    )
    @GetMapping("/managed")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('PM', 'ADMIN')")
    fun getManagedProjects(
        @AuthenticationPrincipal jwt: Jwt,
        authentication: Authentication,
    ): List<ManagedProjectResponse> {
        return projectManagerService.getManagedProjects(
            authId = jwt.subject,
            isAdmin = projectAuth.isAdmin(authentication),
        )
    }

    /**
     * Returns detailed information for one project.
     *
     * @param projectId Project identifier.
     * @return The requested project.
     */
    @Operation(
        summary = "Get project by id",
        description = "Returns project metadata, connected sources and assigned users.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Project found"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "No access to this project"),
            ApiResponse(responseCode = "404", description = "Project not found"),
        ],
    )
    @GetMapping("/{projectId}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@projectAuth.canAccessProject(authentication, #projectId)")
    fun getProjectById(
        @Parameter(description = "UUID of the project to retrieve")
        @PathVariable projectId: UUID,
    ): AdminProjectDetailResponse {
        return adminProjectService.getProjectById(projectId)
    }

    /**
     * Returns all users assigned to a managed project.
     *
     * @param projectId Project identifier.
     * @return Users assigned to the project.
     */
    @Operation(
        summary = "Get project users",
        description = "Returns all users assigned to a project the caller manages.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Project users returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Caller does not manage this project"),
            ApiResponse(responseCode = "404", description = "Project not found"),
        ],
    )
    @GetMapping("/{projectId}/users")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
    fun getProjectUsers(
        @Parameter(description = "UUID of the project whose users should be returned")
        @PathVariable projectId: UUID,
    ): List<ProjectUserResponse> {
        return adminProjectService.getProjectUsers(projectId)
    }

    /**
     * Assigns users to a managed project.
     *
     * @param projectId Project identifier.
     * @param request User assignment payload.
     * @return The full assigned-user list after the operation.
     */
    @Operation(
        summary = "Assign project users",
        description = "Assigns one or more users to a project the caller manages.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Users assigned successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Caller does not manage this project"),
            ApiResponse(responseCode = "404", description = "Project or user not found"),
        ],
    )
    @PostMapping("/{projectId}/users")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
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
     * Removes a user from a managed project.
     *
     * @param projectId Project identifier.
     * @param userId User identifier.
     */
    @Operation(
        summary = "Remove project user",
        description = "Removes a user assignment from a project the caller manages.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "User removed from project successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Caller does not manage this project"),
            ApiResponse(responseCode = "404", description = "Project or assignment not found"),
            ApiResponse(responseCode = "409", description = "User is the assigned project manager"),
        ],
    )
    @DeleteMapping("/{projectId}/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("@projectAuth.canManageProject(authentication, #projectId)")
    fun removeUser(
        @Parameter(description = "UUID of the project")
        @PathVariable projectId: UUID,
        @Parameter(description = "UUID of the user to remove from the project")
        @PathVariable userId: UUID,
    ) {
        adminProjectService.removeUser(projectId, userId)
    }
}
