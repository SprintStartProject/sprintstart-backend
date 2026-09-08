package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.request.AssignProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.model.request.CreateProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.model.request.UpdateRoleSkillsRequest
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.UpdateRoleSkillsResponse
import com.sprintstart.sprintstartbackend.user.model.response.user.ProjectRoleSummary
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
import org.springframework.web.bind.annotation.PutMapping
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
     * The roles a user holds on one project.
     *
     * @param projectId The UUID of the project.
     * @param userId The UUID of the user.
     * @return The roles held there; 404 when the user is not on that project.
     */
    @Operation(
        summary = "Roles a user holds on a project",
        description = "What this person does on this project, which is where roles are held.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Roles returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "The user is not assigned to that project"),
        ],
    )
    @GetMapping("/projects/{projectId}/users/{userId}/project-roles")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun getRolesForUserOnProject(
        @Parameter(description = "UUID of the project") @PathVariable projectId: UUID,
        @Parameter(description = "UUID of the user") @PathVariable userId: UUID,
    ): List<ProjectRoleSummary> = projectRoleService.getRolesForUserOnProject(userId, projectId)

    /**
     * Gives a user a project role, on one project.
     *
     * The project is in the path rather than the body because it is not optional: a role is held
     * *on a project*, so there is no meaningful call without one.
     *
     * @param projectId The UUID of the project the role is held on.
     * @param userId The UUID of the user.
     * @param request The request containing the role ID to assign.
     */
    @Operation(
        summary = "Assign role to user, checking project membership",
        description = "Gives a user a project role. The role is held on the person and applies " +
            "everywhere; the path's project only requires that they are already on it.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Role assigned to user successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(
                responseCode = "404",
                description = "Project role not found, or the user is not assigned to that project",
            ),
        ],
    )
    @PostMapping("/projects/{projectId}/users/{userId}/project-roles")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun assignRoleToUser(
        @Parameter(description = "UUID of the project") @PathVariable projectId: UUID,
        @Parameter(description = "UUID of the user") @PathVariable userId: UUID,
        @RequestBody request: AssignProjectRoleRequest,
    ) {
        projectRoleService.assignRoleToUser(userId, projectId, request.roleId)
    }

    /**
     * Takes a project role off a user, checking first that they are on [projectId].
     *
     * The role is removed everywhere, not only on this project — a role is held on the
     * person, so there is no per-project copy to take away. The project is a guard on who may be
     * edited, not a narrowing of what is edited.
     *
     * @param projectId The UUID of the project the user must be on.
     * @param userId The UUID of the user.
     * @param roleId The UUID of the project role to remove.
     */
    @Operation(
        summary = "Unassign role from user, checking project membership",
        description = "Removes a project role from a user. The role is held on the person, so it " +
            "is removed on every project; the path's project only gates who may be edited.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Role removed from user successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "The user is not assigned to that project"),
        ],
    )
    @DeleteMapping("/projects/{projectId}/users/{userId}/project-roles/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun unassignRoleFromUser(
        @Parameter(description = "UUID of the project") @PathVariable projectId: UUID,
        @Parameter(description = "UUID of the user") @PathVariable userId: UUID,
        @Parameter(description = "UUID of the project role to remove") @PathVariable roleId: UUID,
    ) {
        projectRoleService.unassignRoleFromUser(userId, projectId, roleId)
    }

    /**
     * Assigns a project role to a user.
     *
     * The same write as the project-scoped route without the membership check, for callers that
     * have no project in hand. A role is held on the person, so naming a project is not needed to
     * express it.
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
     * The counterpart to [assignRoleToUser]; removes the role on every project, as the
     * project-scoped route also does.
     *
     * @param userId The UUID of the user.
     * @param roleId The UUID of the project role to remove.
     */
    @Operation(summary = "Unassign role from user", description = "Removes a project role from a user.")
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

    /**
     * Retrieves all skills linked to a project role.
     *
     * @param roleId The UUID of the project role.
     * @return List of skills linked to the role.
     */
    @Operation(
        summary = "Get skills for project role",
        description = "Retrieves all skills currently linked to a specific project role.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skills returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "Project role not found"),
        ],
    )
    @GetMapping("/projectRoles/{roleId}/skills")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR', 'USER')")
    fun getSkillsForRole(
        @Parameter(description = "UUID of the project role") @PathVariable roleId: UUID,
    ): List<GetSkillResponse> {
        return projectRoleService.getSkillsForRole(roleId)
    }

    /**
     * Replaces the full set of skills linked to a project role.
     *
     * @param roleId The UUID of the project role.
     * @param request The request containing the full set of skill IDs to link to the role.
     * @return The skills linked to the role after the update.
     */
    @Operation(
        summary = "Set skills for project role",
        description = "Replaces the full set of skills linked to a project role.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skills updated successfully"),
            ApiResponse(responseCode = "400", description = "Update would leave a skill with no project role"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "Project role or skill not found"),
        ],
    )
    @PutMapping("/projectRoles/{roleId}/skills")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun setSkillsForRole(
        @Parameter(description = "UUID of the project role") @PathVariable roleId: UUID,
        @RequestBody request: UpdateRoleSkillsRequest,
    ): List<UpdateRoleSkillsResponse> {
        return projectRoleService.setSkillsForRole(roleId, request)
    }
}
