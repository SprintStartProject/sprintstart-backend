package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.dto.DeleteUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.GetUserResponse
import com.sprintstart.sprintstartbackend.user.model.dto.PatchMeRequest
import com.sprintstart.sprintstartbackend.user.model.dto.PatchUserRequest
import com.sprintstart.sprintstartbackend.user.model.dto.UpdateUserEnabledRequest
import com.sprintstart.sprintstartbackend.user.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST API for user self-service operations.
 *
 * These endpoints operate on the authenticated user resolved from the JWT subject.
 * Business logic is delegated to [UserService].
 */
@Tag(
    name = "Current User",
    description = "Self-service endpoints for the currently authenticated user.",
)
@RestController
@RequestMapping("/api/v1/users")
class UserSelfController(
    private val userService: UserService,
) {
    /**
     * Returns the authenticated user's profile.
     *
     * @param jwt The authenticated JWT containing the caller subject.
     * @return The current user's profile.
     */
    @Operation(
        summary = "Get current user",
        description = "Returns the combined SprintStart and Keycloak projection for the authenticated user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User found"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access own profile"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @GetMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('USER')")
    fun getMe(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): GetUserResponse = userService.getMe(jwt.subject)

    /**
     * Partially updates the authenticated user's profile.
     *
     * Only fields present in [request] are changed; omitted fields remain unchanged.
     * Passwords and permission groups are handled outside of this self-service endpoint.
     *
     * @param request The partial update payload for the authenticated user.
     * @param jwt The authenticated JWT containing the caller subject.
     * @return The updated current user profile.
     */
    @Operation(
        summary = "Patch current user",
        description = "Updates editable current-user profile fields. Passwords are handled by Keycloak directly.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User patched successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to update own profile"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @PatchMapping("/me")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('USER')")
    fun patchMe(
        @Valid @RequestBody request: PatchMeRequest,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): GetUserResponse = userService.patchMe(jwt.subject, request)
}

/**
 * REST API for administrative user management.
 *
 * These endpoints operate on explicitly selected users and orchestrate local user projections
 * with Keycloak state where necessary.
 *
 * Business logic is delegated to [UserService].
 */
@Tag(
    name = "Admin Users",
    description = "Administrative endpoints for managing users through SprintStart and Keycloak orchestration.",
)
@RestController
@RequestMapping("/api/v1/admin/users")
class AdminUserController(
    private val userService: UserService,
) {
    /**
     * Returns all users visible to administrators.
     *
     * @return A list of all user profiles.
     */
    @Operation(
        summary = "Get all users",
        description = "Returns all user profiles visible for administration.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Users returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access all users"),
        ],
    )
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun getAllUsers(): List<GetUserResponse> = userService.getAllUsers()

    /**
     * Returns a single user by UUID.
     *
     * @param id The UUID of the user to retrieve.
     * @return The matching user profile.
     */
    @Operation(
        summary = "Get user by id",
        description = "Returns a single user profile by SprintStart user UUID.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User found"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access this user"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @GetMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun getUserById(
        @Parameter(description = "UUID of the user to retrieve")
        @PathVariable id: UUID,
    ): GetUserResponse = userService.getUserById(id)

    /**
     * Partially updates a user by UUID.
     *
     * Only fields present in [request] are changed; omitted fields remain unchanged.
     * Profile and permission group changes are forwarded to Keycloak where applicable.
     *
     * @param id The UUID of the user to patch.
     * @param request The partial update payload.
     * @return The patched user profile.
     */
    @Operation(
        summary = "Patch user base fields",
        description = "Updates editable base fields, working area and permission group. " +
            "Enabled state has a dedicated endpoint.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User patched successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to patch this user"),
            ApiResponse(responseCode = "404", description = "User not found"),
            ApiResponse(responseCode = "502", description = "Keycloak admin request failed"),
        ],
    )
    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun patchUserById(
        @Parameter(description = "UUID of the user to patch")
        @PathVariable id: UUID,
        @Valid @RequestBody request: PatchUserRequest,
    ): GetUserResponse = userService.patchAdminUserById(id, request)

    /**
     * Enables or disables a user by UUID.
     *
     * The enabled state is changed in Keycloak and then reflected in the local projection.
     *
     * @param id The UUID of the user whose account status should be updated.
     * @param request The target enabled state.
     * @return The updated user profile.
     * Todo: split into /enable and /disable endpoints
     */
    @Operation(
        summary = "Patch user enabled status",
        description = "Enables or disables the Keycloak account through the backend orchestration layer.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User enabled status updated successfully"),
            ApiResponse(responseCode = "400", description = "Invalid request body"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to update this user"),
            ApiResponse(responseCode = "404", description = "User not found"),
            ApiResponse(responseCode = "502", description = "Keycloak admin request failed"),
        ],
    )
    @PatchMapping("/{id}/enabled")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun updateUserEnabledById(
        @Parameter(description = "UUID of the user whose account status should be updated")
        @PathVariable id: UUID,
        @Valid @RequestBody request: UpdateUserEnabledRequest,
    ): GetUserResponse = userService.updateUserEnabledById(id, request)

    /**
     * Deletes a user by UUID.
     *
     * The Keycloak account is deleted before the local user projection is removed.
     *
     * @param id The UUID of the user to delete.
     * @return A response confirming which user was deleted.
     * Todo: remove return type
     */
    @Operation(
        summary = "Delete user by id",
        description = "Permanently deletes the user account in Keycloak and removes the local projection.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "User deleted successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete this user"),
            ApiResponse(responseCode = "404", description = "User not found"),
            ApiResponse(responseCode = "502", description = "Keycloak admin request failed"),
        ],
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    fun deleteUserById(
        @Parameter(description = "UUID of the user to delete")
        @PathVariable id: UUID,
    ): DeleteUserResponse = userService.deleteAdminUserById(id)
}
