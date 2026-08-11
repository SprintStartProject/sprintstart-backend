package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.CreateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.CreateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.GetBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintCheckOptionService
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
@RequestMapping("/api/v1/onboarding/blueprint/checks")
class BlueprintCheckOptionController(
    private val blueprintCheckOptionService: BlueprintCheckOptionService,
) {
    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/questions/{questionId}/options")
    fun getBlueprintCheckOptionsForQuestion(
        @PathVariable questionId: UUID,
    ): List<GetBlueprintCheckOptionResponse> {
        return blueprintCheckOptionService.getBlueprintCheckOptionsForQuestion(questionId)
    }

    @ResponseStatus(HttpStatus.OK)
    @GetMapping("/options/{optionId}")
    fun getBlueprintCheckOptionById(
        @PathVariable optionId: UUID,
    ): GetBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.getBlueprintCheckOptionById(optionId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/questions/{questionId}/options")
    fun createBlueprintCheckOptionForQuestion(
        @PathVariable questionId: UUID,
        @RequestBody request: CreateBlueprintCheckOptionRequest,
    ): CreateBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.createBlueprintCheckOptionForQuestion(questionId, request)
    }

    @ResponseStatus(HttpStatus.OK)
    @PutMapping("/options/{optionId}")
    fun updateBlueprintCheckOptionById(
        @PathVariable optionId: UUID,
        @RequestBody request: UpdateBlueprintCheckOptionRequest,
    ): UpdateBlueprintCheckOptionResponse {
        return blueprintCheckOptionService.updateBlueprintCheckOptionById(optionId, request)
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/options/{optionId}")
    fun deleteBlueprintCheckOptionById(
        @PathVariable optionId: UUID,
    ) {
        blueprintCheckOptionService.deleteBlueprintCheckOptionById(optionId)
    }
}
