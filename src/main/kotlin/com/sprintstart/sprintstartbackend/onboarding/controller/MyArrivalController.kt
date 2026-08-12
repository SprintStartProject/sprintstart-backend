package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toMyArrivalResponse
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.arrival.ArrivalStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.arrival.MyArrivalResponse
import com.sprintstart.sprintstartbackend.onboarding.service.ArrivalEvidenceService
import com.sprintstart.sprintstartbackend.onboarding.service.ArrivalStepService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The authenticated hire's own arrival steps — what has to be true before they can work, and what
 * they have settled.
 *
 * Not project-scoped, unlike the board: an arrival step is a fact about a person (they have a
 * GitHub account, their machine builds), and it does not stop being true because they switched to
 * looking at another project. The list is the union across every project they belong to, plus the
 * company-wide steps everybody gets.
 *
 * **Nothing here gates anything.** An outstanding step is a value in the response, never a 403, and
 * no other endpoint consults these rows before serving a hire.
 */
@RestController
@RequestMapping("/api/v1/onboarding")
@Tag(name = "Onboarding - My Arrival", description = "The authenticated hire's own arrival steps")
class MyArrivalController(
    private val arrivalStepService: ArrivalStepService,
    private val arrivalEvidenceService: ArrivalEvidenceService,
) {
    /**
     * Returns the caller's arrival steps with their state, counted by how each was established.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return The steps; empty when nobody has authored any, which is a real answer rather than an
     * error.
     */
    @Operation(
        summary = "Get the caller's arrival steps",
        description = "Company-wide steps plus those of every project the caller belongs to, each " +
            "with whether they have settled it. Counts are reported per rigor and never blended " +
            "into a single completion figure.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Steps returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated subject"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/me/arrival")
    @PreAuthorize("hasRole('USER')")
    fun getMyArrival(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): MyArrivalResponse = arrivalStepService.forCaller(jwt.subject).toMyArrivalResponse()

    /**
     * Re-checks the steps the system can observe for the caller, and returns them as they stand.
     *
     * Separate from the board read on purpose: that read touches nothing but the database, because
     * a board that waits on GitHub to open is a board nobody opens. The client calls this once
     * after the card has rendered — the same split the diagram card makes.
     *
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return The caller's steps, including anything this call just settled.
     */
    @Operation(
        summary = "Re-check the caller's observable arrival steps",
        description = "Looks at what the system can verify for itself -- currently whether their " +
            "declared GitHub account exists, and whether they have produced any work. Observation " +
            "settles a step; failing to observe never unsettles one, and an outage records nothing.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Steps returned, refreshed"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No user found for the authenticated subject"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/arrival/refresh")
    @PreAuthorize("hasRole('USER')")
    suspend fun refreshMyArrival(
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): MyArrivalResponse = arrivalEvidenceService.refreshForCaller(jwt.subject).toMyArrivalResponse()

    /**
     * Records that the caller has done the step [key].
     *
     * Idempotent: confirming an already-settled step returns it unchanged, keeping the original
     * settlement time.
     *
     * @param key The step's stable key.
     * @param jwt Authenticated JWT used to resolve the current user.
     * @return The step with its new state.
     */
    @Operation(
        summary = "Confirm an arrival step",
        description = "Records that the caller has done this step, with rigor DECLARED. Idempotent. " +
            "A step settled by observation cannot be confirmed this way.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Step confirmed"),
            ApiResponse(responseCode = "400", description = "This step is settled by observation, not by the hire"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No such step applies to this user"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/me/arrival/{key}/confirm")
    @PreAuthorize("hasRole('USER')")
    fun confirmArrivalStep(
        @PathVariable key: String,
        @Parameter(hidden = true)
        @AuthenticationPrincipal jwt: Jwt,
    ): ArrivalStepResponse = arrivalStepService.confirmForCaller(jwt.subject, key).toResponse()
}
