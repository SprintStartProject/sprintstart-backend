package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.CreateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.CreateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.GetBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintStepService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
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
class BlueprintStepController(
    private val blueprintStepService: BlueprintStepService,
) {
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/phases/{phaseId}/steps")
    fun getBlueprintStepsForPhase(
        @PathVariable phaseId: UUID,
    ): List<GetBlueprintStepResponse> {
        return blueprintStepService.getBlueprintStepForPhase(phaseId)
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/steps/{stepId}")
    fun getBlueprintStepById(
        @PathVariable stepId: UUID,
    ): GetBlueprintStepResponse {
        return blueprintStepService.getBlueprintStepById(stepId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/phases/{phaseId}/steps")
    fun createBlueprintStepForPhase(
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintStepRequest,
    ): CreateBlueprintStepResponse {
        return blueprintStepService.createBlueprintStepForPhase(phaseId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/steps/{stepId}")
    fun updateBlueprintStepById(
        @PathVariable stepId: UUID,
        @RequestBody request: UpdateBlueprintStepRequest,
    ): UpdateBlueprintStepResponse {
        return blueprintStepService.updateBlueprintStepById(stepId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/steps/{stepId}/position")
    fun updateBlueprintStepPositionById(
        @PathVariable stepId: UUID,
        @Valid @RequestBody request: UpdateBlueprintStepPositionRequest,
    ): List<UpdateBlueprintStepPositionResponse> {
        return blueprintStepService.updateBlueprintStepPositionById(stepId, request)
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/steps/{stepId}")
    fun deleteBlueprintStepById(
        @PathVariable stepId: UUID,
    ) {
        blueprintStepService.deleteBlueprintStepById(stepId)
    }
}
