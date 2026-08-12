package com.sprintstart.sprintstartbackend.onboarding.controller

import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.arrival.CreateArrivalStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.arrival.ReorderArrivalStepsRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.arrival.UpdateArrivalStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.arrival.ArrivalStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.arrival.DerivableArrivalStepResponse
import com.sprintstart.sprintstartbackend.onboarding.service.ArrivalStepService
import io.swagger.v3.oas.annotations.Operation
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * Authoring for arrival steps — the list of things that have to be true before somebody can work.
 *
 * Scope is chosen by the optional `projectId` parameter: **omitted means company-wide**, which is
 * the common case by design — account creation and paperwork do not differ per project, and making
 * each PM re-author them is the effort this model exists to avoid.
 *
 * Reads are open to `HR` alongside `ADMIN`/`PM`, writes are not, mirroring the split every other
 * authoring surface here uses. Worth revisiting: much of this content (paperwork, badges,
 * accounts) is HR's to own rather than a PM's, so HR-writes-company-steps is a plausible change —
 * it is left alone here because changing a role split is a decision, not a detail.
 */
@RestController
@RequestMapping("/api/v1/onboarding/arrival-steps")
@Tag(name = "Onboarding - Arrival Authoring", description = "Authoring the arrival step list")
class ArrivalAuthoringController(
    private val arrivalStepService: ArrivalStepService,
) {
    /**
     * Lists the authored steps in a scope.
     *
     * @param projectId Omit for the company-wide list.
     * @return The steps in their configured order.
     */
    @Operation(
        summary = "List arrival steps in a scope",
        description = "Omit projectId for the company-wide list.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Steps returned successfully"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun listArrivalSteps(
        @RequestParam(required = false) projectId: UUID?,
    ): List<ArrivalStepResponse> = arrivalStepService.listForAuthoring(projectId).map { it.toResponse() }

    /**
     * The steps the system can check for itself, and whether each is already on the list.
     *
     * @return The catalog; `added` says which are already authored.
     */
    @Operation(
        summary = "List the arrival steps the system can verify",
        description = "A derivation is code, so these are the only keys that can be checked " +
            "automatically. Nothing is added by default -- an admin picks the ones that apply.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Catalog returned"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/derivable")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    fun listDerivableSteps(): List<DerivableArrivalStepResponse> =
        arrivalStepService.derivable().map { (derivation, added) ->
            DerivableArrivalStepResponse(
                key = derivation.stepKey,
                suggestedTitle = derivation.suggestedTitle,
                suggestedDescription = derivation.suggestedDescription,
                selfConfirmable = derivation.selfConfirmable,
                added = added,
            )
        }

    /**
     * Creates a step.
     *
     * @param request The step to create; omit `projectId` for a company-wide step.
     * @return The created step.
     */
    @Operation(summary = "Create an arrival step")
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Step created"),
            ApiResponse(responseCode = "400", description = "Invalid key or blank title"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "409", description = "A step with that key already exists in this scope"),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun createArrivalStep(
        @Valid @RequestBody request: CreateArrivalStepRequest,
    ): ArrivalStepResponse =
        arrivalStepService
            .create(
                key = request.key,
                projectId = request.projectId,
                title = request.title,
                description = request.description,
                href = request.href,
                position = request.position,
                settledBy = request.settledBy,
            ).toResponse()

    /**
     * Updates a step's wording, link, ordering or settlement mechanism. The key cannot be changed.
     *
     * @param key The step's stable key.
     * @param projectId Omit for a company-wide step.
     * @return The updated step.
     */
    @Operation(
        summary = "Update an arrival step",
        description = "Omitted fields are unchanged. The key is immutable -- hires' state points at " +
            "it, so renaming would orphan their records.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Step updated"),
            ApiResponse(responseCode = "400", description = "Blank title"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No such step in this scope"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PatchMapping("/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun updateArrivalStep(
        @PathVariable key: String,
        @RequestParam(required = false) projectId: UUID?,
        @RequestBody request: UpdateArrivalStepRequest,
    ): ArrivalStepResponse =
        arrivalStepService
            .update(
                key = key,
                projectId = projectId,
                title = request.title,
                description = request.description,
                href = request.href,
                position = request.position,
                settledBy = request.settledBy,
            ).toResponse()

    /**
     * Applies a whole ordering at once.
     *
     * @param projectId Omit for the company-wide list.
     * @param request Every key in the scope, in the order they should appear.
     * @return The steps in their new order.
     */
    @Operation(
        summary = "Reorder arrival steps",
        description = "Takes the complete ordering rather than a from/to pair.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Steps reordered"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "A key in the ordering is not a step in this scope"),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PostMapping("/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun reorderArrivalSteps(
        @RequestParam(required = false) projectId: UUID?,
        @RequestBody request: ReorderArrivalStepsRequest,
    ): List<ArrivalStepResponse> =
        arrivalStepService.reorder(projectId, request.orderedKeys).map { it.toResponse() }

    /**
     * Deletes a step definition. Hires' records of having settled it survive.
     *
     * @param key The step's stable key.
     * @param projectId Omit for a company-wide step.
     */
    @Operation(
        summary = "Delete an arrival step",
        description = "Removes the definition. Hires' settled records survive, keyed by the step " +
            "key, so re-adding the same key restores them.",
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Step deleted"),
            ApiResponse(responseCode = "401", description = "Authentication required"),
            ApiResponse(responseCode = "403", description = "Insufficient role"),
            ApiResponse(responseCode = "404", description = "No such step in this scope"),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{key}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PM')")
    fun deleteArrivalStep(
        @PathVariable key: String,
        @RequestParam(required = false) projectId: UUID?,
    ) {
        arrivalStepService.delete(key, projectId)
    }
}
