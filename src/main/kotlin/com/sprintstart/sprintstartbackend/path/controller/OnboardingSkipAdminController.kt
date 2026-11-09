package com.sprintstart.sprintstartbackend.path.controller

import com.sprintstart.sprintstartbackend.path.model.request.skip.ReviewOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.path.model.response.skip.GetAllOnboardingSkipsResponse
import com.sprintstart.sprintstartbackend.path.model.response.skip.GetOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.path.model.response.skip.ReviewOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.path.service.OnboardingSkipService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
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
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Exposes the staff side of onboarding skip requests; the hire's own are in [OnboardingSkipController]. */
@RestController
@RequestMapping("/api/v1/admin/onboarding")
@Tag(
    name = "Onboarding - Admin controls for skips",
    description = "Create, retrieve, review, and delete onboarding skip requests",
)
class OnboardingSkipAdminController(
    private val onboardingSkipService: OnboardingSkipService,
) {
    /**
     * Returns every onboarding skip request in the system.
     *
     * @return All skip requests ordered by creation time.
     */
    @Operation(
        summary = "Get all onboarding skips",
        description = "Returns every onboarding skip request in the system.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skips returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding skips"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/skips")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getAllSkips(): List<GetAllOnboardingSkipsResponse> {
        return onboardingSkipService.getAllSkips()
    }

    /**
     * Returns every onboarding skip request belonging to one user.
     *
     * @param userId Identifier of the user whose skips should be returned.
     * @return All skip requests on the requested user's onboarding path.
     */
    @Operation(
        summary = "Get onboarding skips by user ID",
        description = "Returns all onboarding skip requests belonging to the specified user.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skips returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding skips"),
            ApiResponse(responseCode = "404", description = "No user found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/users/{userId}/skips")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getAllSkipsByUserId(
        @Parameter(description = "UUID of the user")
        @PathVariable userId: UUID,
    ): List<GetOnboardingSkipResponse> {
        return onboardingSkipService.getAllSkipsByUserId(userId)
    }

    /**
     * Returns every onboarding skip request attached to one step.
     *
     * @param stepId Identifier of the step whose skips should be returned.
     * @return All skip requests attached to the requested step.
     */
    @Operation(
        summary = "Get onboarding skips by step ID",
        description = "Returns all onboarding skip requests attached to the specified step.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skips returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding skips"),
            ApiResponse(responseCode = "404", description = "No onboarding step found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/steps/{stepId}/skips")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getSkipsByStepId(
        @Parameter(description = "UUID of the onboarding step")
        @PathVariable stepId: UUID,
    ): List<GetOnboardingSkipResponse> {
        return onboardingSkipService.getAllSkipsByStepId(stepId)
    }

    /**
     * Returns one onboarding skip request by ID.
     *
     * @param skipId Identifier of the skip to return.
     * @return The requested skip.
     */
    @Operation(
        summary = "Get onboarding skip by ID",
        description = "Returns one onboarding skip request by its identifier.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skip returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to access onboarding skips"),
            ApiResponse(responseCode = "404", description = "No onboarding skip found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/skips/{skipId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun getSkipById(
        @Parameter(description = "UUID of the onboarding skip")
        @PathVariable skipId: UUID,
    ): GetOnboardingSkipResponse {
        return onboardingSkipService.getSkipById(skipId)
    }

    /**
     * Accepts one pending onboarding skip request.
     *
     * Accepting a skip resolves the request and marks the parent step as skipped.
     *
     * @param skipId Identifier of the skip to accept.
     * @param request Review payload containing the admin comment.
     * @return The resolved skip review response.
     */
    @Operation(
        summary = "Accept onboarding skip",
        description = "Accepts a pending onboarding skip request and marks the parent step as skipped.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skip accepted successfully"),
            ApiResponse(responseCode = "400", description = "Only pending skips can be accepted"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to review onboarding skips"),
            ApiResponse(responseCode = "404", description = "No onboarding skip found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/skips/{skipId}/accept")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun acceptSkipById(
        @Parameter(description = "UUID of the onboarding skip to accept")
        @PathVariable skipId: UUID,
        @Valid @RequestBody request: ReviewOnboardingSkipRequest,
    ): ReviewOnboardingSkipResponse {
        return onboardingSkipService.acceptSkipById(skipId, request)
    }

    /**
     * Denies one pending onboarding skip request.
     *
     * Denying a skip resolves the request and leaves the parent step waiting.
     *
     * @param skipId Identifier of the skip to deny.
     * @param request Review payload containing the admin comment.
     * @return The resolved skip review response.
     */
    @Operation(
        summary = "Deny onboarding skip",
        description = "Denies a pending onboarding skip request and leaves the parent step waiting.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Skip denied successfully"),
            ApiResponse(responseCode = "400", description = "Only pending skips can be denied"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to review onboarding skips"),
            ApiResponse(responseCode = "404", description = "No onboarding skip found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/skips/{skipId}/deny")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun denySkipById(
        @Parameter(description = "UUID of the onboarding skip to deny")
        @PathVariable skipId: UUID,
        @Valid @RequestBody request: ReviewOnboardingSkipRequest,
    ): ReviewOnboardingSkipResponse {
        return onboardingSkipService.denySkipById(skipId, request)
    }

    /**
     * Deletes one onboarding skip request by ID.
     *
     * Only pending skip requests can be deleted.
     *
     * @param skipId Identifier of the skip to delete.
     */
    @Operation(
        summary = "Delete onboarding skip",
        description = "Deletes one onboarding skip request by its identifier.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Skip deleted successfully"),
            ApiResponse(responseCode = "400", description = "Only pending skips can be deleted"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role to delete onboarding skips"),
            ApiResponse(responseCode = "404", description = "No onboarding skip found with the given ID"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/skips/{skipId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun deleteSkipById(
        @Parameter(description = "UUID of the onboarding skip to delete")
        @PathVariable skipId: UUID,
    ) {
        onboardingSkipService.deleteSkipById(skipId)
    }
}
