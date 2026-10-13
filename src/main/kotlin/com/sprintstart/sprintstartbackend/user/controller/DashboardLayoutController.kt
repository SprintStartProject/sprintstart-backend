package com.sprintstart.sprintstartbackend.user.controller

import com.sprintstart.sprintstartbackend.user.model.request.dashboard.SaveDashboardLayoutRequest
import com.sprintstart.sprintstartbackend.user.model.response.dashboard.DashboardLayoutResponse
import com.sprintstart.sprintstartbackend.user.service.DashboardLayoutService
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
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * How the signed-in user has arranged their dashboard.
 *
 * Self-serve only: the user is always resolved from the token and no endpoint takes a user id, so
 * there is no "read somebody else's layout" to get the authorisation wrong on.
 *
 * The per-board arrangement (areas, folds, sizes, pins on `/board`) is a separate document per
 * board — see `BoardStructureController`. This one is the user-level half: one dashboard per person.
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(
    name = "Current User - Dashboard layout",
    description = "Which widgets you placed on your dashboard, in which order and at which size",
)
class DashboardLayoutController(
    private val dashboardLayoutService: DashboardLayoutService,
) {
    /**
     * Returns the caller's dashboard arrangement for the given client layout version.
     *
     * No arrangement, or one written under another version, answers 200 with no items and a null
     * `updatedAt`, which tells the client to show its default.
     *
     * @param jwt The authenticated JWT containing the caller subject.
     * @param version The layout version the client understands.
     * @return The stored arrangement, or the empty one.
     */
    @Operation(
        summary = "How I have arranged my dashboard",
        description = "Returns the widgets placed on the caller's dashboard, in order, with their sizes.\n\n" +
            "`version` is the layout version the client understands. A layout stored under any " +
            "other version, or no layout at all, answers with no items and a null `updatedAt` " +
            "rather than a 404: it means \"show the default\".",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Arrangement returned, possibly empty"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/dashboard/layout")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun getMyLayout(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestParam version: Int,
    ): DashboardLayoutResponse = dashboardLayoutService.read(jwt.subject, version)

    /**
     * Replaces the caller's dashboard arrangement.
     *
     * @param jwt The authenticated JWT containing the caller subject.
     * @param request The whole arrangement and the version it was written under.
     * @return The stored arrangement with its new `updatedAt`.
     */
    @Operation(
        summary = "Arrange my dashboard",
        description = "Replaces the whole arrangement. Send all of it rather than the part that " +
            "changed. Widget ids and sizes are the client's vocabulary and are stored as given; " +
            "the client drops what it no longer knows when it reads them back.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Arrangement stored"),
            ApiResponse(responseCode = "400", description = "Invalid arrangement"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/me/dashboard/layout")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun saveMyLayout(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
        @RequestBody @Valid request: SaveDashboardLayoutRequest,
    ): DashboardLayoutResponse = dashboardLayoutService.write(jwt.subject, request)

    /**
     * Forgets the caller's dashboard arrangement, so the next read answers with the default.
     *
     * @param jwt The authenticated JWT containing the caller subject.
     */
    @Operation(
        summary = "Reset my dashboard",
        description = "Removes the stored arrangement. Succeeds whether or not one was stored.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Arrangement removed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "404", description = "User not found"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me/dashboard/layout")
    @PreAuthorize("hasAnyRole('USER', 'PM', 'HR', 'ADMIN')")
    fun resetMyLayout(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ) {
        dashboardLayoutService.clear(jwt.subject)
    }
}
