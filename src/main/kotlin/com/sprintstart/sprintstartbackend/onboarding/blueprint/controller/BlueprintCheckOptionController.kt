package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.CreateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.DeleteBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.CreateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.GetBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintCheckOptionService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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

/**
 * Exposes global administrative blueprint check option endpoints.
 *
 * All routes use [BlueprintScope.Global] and require the administrator role. The controller translates HTTP input
 * into service calls; authorization of blueprint state, revisions, and graph rules remains in the service layer.
 */
@RestController
@RequestMapping("/api/v1/onboarding/blueprints/checks")
class BlueprintCheckOptionAdminController(
    private val blueprintCheckOptionService: BlueprintCheckOptionService,
) {
    /**
     * Returns check options for question.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param questionId Blueprint check-question identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns check options for question",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns check options for question successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/questions/{questionId}/options")
    fun getBlueprintCheckOptionsForQuestion(
        @PathVariable questionId: UUID,
    ): List<GetBlueprintCheckOptionResponse> {
        return blueprintCheckOptionService.getBlueprintCheckOptionsForQuestion(
            BlueprintScope.Global,
            questionId,
        )
    }

    /**
     * Returns check option by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param optionId Blueprint check-option identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns check option by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns check option by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/options/{optionId}")
    fun getBlueprintCheckOptionById(
        @PathVariable optionId: UUID,
    ): GetBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.getBlueprintCheckOptionById(
            BlueprintScope.Global,
            optionId,
        )
    }

    /**
     * Creates check option for question.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param questionId Blueprint check-question identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates check option for question",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Blueprint resource created",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/questions/{questionId}/options")
    fun createBlueprintCheckOptionForQuestion(
        @PathVariable questionId: UUID,
        @RequestBody request: CreateBlueprintCheckOptionRequest,
    ): CreateBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.createBlueprintCheckOptionForQuestion(
            BlueprintScope.Global,
            questionId,
            request,
        )
    }

    /**
     * Updates check option by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param optionId Blueprint check-option identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates check option by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates check option by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/options/{optionId}")
    fun updateBlueprintCheckOptionById(
        @PathVariable optionId: UUID,
        @RequestBody request: UpdateBlueprintCheckOptionRequest,
    ): UpdateBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.updateBlueprintCheckOptionById(
            BlueprintScope.Global,
            optionId,
            request,
        )
    }

    /**
     * Updates check option position by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param optionId Blueprint check-option identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates check option position by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates check option position by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/options/{optionId}/position")
    fun updateBlueprintCheckOptionPositionById(
        @PathVariable optionId: UUID,
        @Valid @RequestBody request: UpdateBlueprintCheckOptionPositionRequest,
    ): List<UpdateBlueprintCheckOptionPositionResponse> {
        return blueprintCheckOptionService.updateBlueprintCheckOptionPositionById(
            BlueprintScope.Global,
            optionId,
            request,
        )
    }

    /**
     * Deletes check option by id.
     *
     * The endpoint delegates to the blueprint service with global scope and is restricted to administrators.
     *
     * @param optionId Blueprint check-option identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     */
    @Operation(
        summary = "Deletes check option by id",
        description = "Uses global scope; the service enforces blueprint state and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Blueprint resource deleted",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/options/{optionId}")
    fun deleteBlueprintCheckOptionById(
        @PathVariable optionId: UUID,
        @RequestBody request: DeleteBlueprintCheckOptionRequest,
    ) {
        blueprintCheckOptionService.deleteBlueprintCheckOptionById(
            BlueprintScope.Global,
            optionId,
            request,
        )
    }
}

/**
 * Exposes project-scoped blueprint check option endpoints.
 *
 * All routes derive [BlueprintScope.Project] from the path's project identifier. Method-level security defines which
 * management roles may call each operation, while the service layer enforces blueprint ownership and editability.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints/checks")
class BlueprintCheckOptionController(
    private val blueprintCheckOptionService: BlueprintCheckOptionService,
) {
    /**
     * Returns check options for question.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param questionId Blueprint check-question identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns check options for question",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns check options for question successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/questions/{questionId}/options")
    fun getBlueprintCheckOptionsForQuestion(
        @PathVariable projectId: UUID,
        @PathVariable questionId: UUID,
    ): List<GetBlueprintCheckOptionResponse> {
        return blueprintCheckOptionService.getBlueprintCheckOptionsForQuestion(
            BlueprintScope.Project(projectId),
            questionId,
        )
    }

    /**
     * Returns check option by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param optionId Blueprint check-option identifier.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Returns check option by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Returns check option by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/options/{optionId}")
    fun getBlueprintCheckOptionById(
        @PathVariable projectId: UUID,
        @PathVariable optionId: UUID,
    ): GetBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.getBlueprintCheckOptionById(
            BlueprintScope.Project(projectId),
            optionId,
        )
    }

    /**
     * Creates check option for question.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param questionId Blueprint check-question identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Creates check option for question",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Blueprint resource created",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/questions/{questionId}/options")
    fun createBlueprintCheckOptionForQuestion(
        @PathVariable projectId: UUID,
        @PathVariable questionId: UUID,
        @RequestBody request: CreateBlueprintCheckOptionRequest,
    ): CreateBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.createBlueprintCheckOptionForQuestion(
            BlueprintScope.Project(projectId),
            questionId,
            request,
        )
    }

    /**
     * Updates check option by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param optionId Blueprint check-option identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates check option by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates check option by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/options/{optionId}")
    fun updateBlueprintCheckOptionById(
        @PathVariable projectId: UUID,
        @PathVariable optionId: UUID,
        @RequestBody request: UpdateBlueprintCheckOptionRequest,
    ): UpdateBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.updateBlueprintCheckOptionById(
            BlueprintScope.Project(projectId),
            optionId,
            request,
        )
    }

    /**
     * Updates check option position by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param optionId Blueprint check-option identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     * @return The service response for the requested blueprint operation.
     */
    @Operation(
        summary = "Updates check option position by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Updates check option position by id successfully",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Request data, position, relationship, or blueprint state is invalid",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/options/{optionId}/position")
    fun updateBlueprintCheckOptionPositionById(
        @PathVariable projectId: UUID,
        @PathVariable optionId: UUID,
        @Valid @RequestBody request: UpdateBlueprintCheckOptionPositionRequest,
    ): List<UpdateBlueprintCheckOptionPositionResponse> {
        return blueprintCheckOptionService.updateBlueprintCheckOptionPositionById(
            BlueprintScope.Project(projectId),
            optionId,
            request,
        )
    }

    /**
     * Deletes check option by id.
     *
     * The endpoint builds a project scope from `projectId` and delegates authorization and business validation to
     * the blueprint service.
     *
     * @param projectId Project whose blueprint is being accessed.
     *
     * @param optionId Blueprint check-option identifier.
     *
     * @param request Request payload containing the mutation data and, where required, the expected revision.
     */
    @Operation(
        summary = "Deletes check option by id",
        description = "Uses project scope; the service enforces ownership, state, and revisions.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Blueprint resource deleted",
            ),
            ApiResponse(
                responseCode = "401",
                description = "Authentication required",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Insufficient role or blueprint scope access",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Requested blueprint resource not found in the selected scope",
            ),
            ApiResponse(
                responseCode = "409",
                description = "Blueprint is not editable or the supplied revision is stale",
            ),
        ],
    )
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @DeleteMapping("/options/{optionId}")
    fun deleteBlueprintCheckOptionById(
        @PathVariable projectId: UUID,
        @PathVariable optionId: UUID,
        @RequestBody request: DeleteBlueprintCheckOptionRequest,
    ) {
        blueprintCheckOptionService.deleteBlueprintCheckOptionById(
            BlueprintScope.Project(projectId),
            optionId,
            request,
        )
    }
}
