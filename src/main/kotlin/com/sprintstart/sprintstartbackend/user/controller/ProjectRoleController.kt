package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.request.AssignProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.model.request.CreateProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.service.ProjectRoleService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST Controller for managing project roles.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Project Roles", description = "Endpoints for managing project roles")
class ProjectRoleController(
    private val projectRoleService: ProjectRoleService,
) {
    /**
     * Retrieves all available project roles.
     *
     * @return List of all project roles.
     */
    @Operation(summary = "Get all project roles", description = "Retrieves a list of all available project roles.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Project roles returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @GetMapping("/projectRoles")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getAllRoles(): List<ProjectRole> {
        return projectRoleService.getAllRoles()
    }

    /**
     * Creates a new project role.
     *
     * @param request The request containing the role details.
     * @return The created project role.
     */
    @Operation(summary = "Create project role", description = "Creates a new project role with the given details.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Project role created successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @PostMapping("/projectRoles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun createRole(
        @RequestBody request: CreateProjectRoleRequest,
    ): ProjectRole {
        return projectRoleService.createRole(request)
    }

    /**
     * Deletes a project role by its ID.
     *
     * @param roleId The UUID of the project role to delete.
     */
    @Operation(summary = "Delete project role", description = "Deletes a project role by its unique identifier.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Project role deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "Project role not found"),
        ],
    )
    @DeleteMapping("/projectRoles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun deleteRole(
        @Parameter(description = "UUID of the project role to delete") @PathVariable roleId: UUID,
    ) {
        projectRoleService.deleteRole(roleId)
    }

    /**
     * Assigns a project role to a user.
     *
     * @param userId The UUID of the user.
     * @param request The request containing the role ID to assign.
     */
    @Operation(summary = "Assign role to user", description = "Assigns a specific project role to a user.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Role assigned to user successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "User or Project role not found"),
        ],
    )
    @PostMapping("/users/{userId}/project-roles")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun assignRoleToUser(
        @Parameter(description = "UUID of the user") @PathVariable userId: UUID,
        @RequestBody request: AssignProjectRoleRequest,
    ) {
        projectRoleService.assignRoleToUser(userId, request.roleId)
    }

    /**
     * Unassigns a project role from a user.
     *
     * @param userId The UUID of the user.
     * @param roleId The UUID of the project role to remove.
     */
    @Operation(summary = "Unassign role from user", description = "Removes a specific project role from a user.")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Role removed from user successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @DeleteMapping("/users/{userId}/project-roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun unassignRoleFromUser(
        @Parameter(description = "UUID of the user") @PathVariable userId: UUID,
        @Parameter(description = "UUID of the project role to remove") @PathVariable roleId: UUID,
    ) {
        projectRoleService.unassignRoleFromUser(userId, roleId)
    }
}
