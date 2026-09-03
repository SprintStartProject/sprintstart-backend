package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.CreateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.DeleteBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.CreateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.GetBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintStepService
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

@RestController
@RequestMapping("/api/v1/onboarding/blueprints")
class BlueprintStepAdminController(
    private val blueprintStepService: BlueprintStepService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/phases/{phaseId}/steps")
    fun getBlueprintStepsForPhase(
        @PathVariable phaseId: UUID,
    ): List<GetBlueprintStepResponse> {
        return blueprintStepService.getBlueprintStepForPhase(BlueprintScope.Global, phaseId)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/steps/{stepId}")
    fun getBlueprintStepById(
        @PathVariable stepId: UUID,
    ): GetBlueprintStepResponse {
        return blueprintStepService.getBlueprintStepById(BlueprintScope.Global, stepId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/phases/{phaseId}/steps")
    fun createBlueprintStepForPhase(
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintStepRequest,
    ): CreateBlueprintStepResponse {
        return blueprintStepService.createBlueprintStepForPhase(BlueprintScope.Global, phaseId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/steps/{stepId}")
    fun updateBlueprintStepById(
        @PathVariable stepId: UUID,
        @RequestBody request: UpdateBlueprintStepRequest,
    ): UpdateBlueprintStepResponse {
        return blueprintStepService.updateBlueprintStepById(BlueprintScope.Global, stepId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/steps/{stepId}/position")
    fun updateBlueprintStepPositionById(
        @PathVariable stepId: UUID,
        @Valid @RequestBody request: UpdateBlueprintStepPositionRequest,
    ): List<UpdateBlueprintStepPositionResponse> {
        return blueprintStepService.updateBlueprintStepPositionById(BlueprintScope.Global, stepId, request)
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/steps/{stepId}")
    fun deleteBlueprintStepById(
        @PathVariable stepId: UUID,
        @RequestBody request: DeleteBlueprintStepRequest,
    ) {
        blueprintStepService.deleteBlueprintStepById(BlueprintScope.Global, stepId, request)
    }
}

@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints")
class BlueprintStepController(
    private val blueprintStepService: BlueprintStepService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/phases/{phaseId}/steps")
    fun getBlueprintStepsForPhase(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
    ): List<GetBlueprintStepResponse> {
        return blueprintStepService.getBlueprintStepForPhase(BlueprintScope.Project(projectId), phaseId)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @GetMapping("/steps/{stepId}")
    fun getBlueprintStepById(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
    ): GetBlueprintStepResponse {
        return blueprintStepService.getBlueprintStepById(BlueprintScope.Project(projectId), stepId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PostMapping("/phases/{phaseId}/steps")
    fun createBlueprintStepForPhase(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintStepRequest,
    ): CreateBlueprintStepResponse {
        return blueprintStepService.createBlueprintStepForPhase(BlueprintScope.Project(projectId), phaseId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/steps/{stepId}")
    fun updateBlueprintStepById(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
        @RequestBody request: UpdateBlueprintStepRequest,
    ): UpdateBlueprintStepResponse {
        return blueprintStepService.updateBlueprintStepById(BlueprintScope.Project(projectId), stepId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @PutMapping("/steps/{stepId}/position")
    fun updateBlueprintStepPositionById(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
        @Valid @RequestBody request: UpdateBlueprintStepPositionRequest,
    ): List<UpdateBlueprintStepPositionResponse> {
        return blueprintStepService.updateBlueprintStepPositionById(BlueprintScope.Project(projectId), stepId, request)
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'PM', 'HR')")
    @DeleteMapping("/steps/{stepId}")
    fun deleteBlueprintStepById(
        @PathVariable projectId: UUID,
        @PathVariable stepId: UUID,
        @RequestBody request: DeleteBlueprintStepRequest,
    ) {
        blueprintStepService.deleteBlueprintStepById(BlueprintScope.Project(projectId), stepId, request)
    }
}
