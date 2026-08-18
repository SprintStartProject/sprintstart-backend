package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.CreateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.CreateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.GetBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintCheckQuestionService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/onboarding/blueprints/")
class BlueprintCheckQuestionController(
    private val blueprintCheckQuestionService: BlueprintCheckQuestionService,
) {
    @GetMapping("/phase/{phaseId}/checks/questions")
    fun getBlueprintCheckQuestionsForPhase(
        @PathVariable phaseId: UUID,
    ): List<GetBlueprintCheckQuestionResponse> {
        return blueprintCheckQuestionService.getBlueprintCheckQuestionsForPhase(phaseId)
    }

    @GetMapping("/checks/questions/{questionId}")
    fun getBlueprintCheckQuestionById(
        @PathVariable questionId: UUID,
    ): GetBlueprintCheckQuestionResponse {
        return blueprintCheckQuestionService.getBlueprintCheckQuestionById(questionId)
    }

    @PostMapping("/phase/{phaseId}/checks/questions")
    fun createBlueprintCheckQuestionForPhase(
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintCheckQuestionRequest,
    ): CreateBlueprintCheckQuestionResponse {
        return blueprintCheckQuestionService.createBlueprintCheckQuestionForPhase(phaseId, request)
    }

    @PutMapping("/checks/question/{questionId}")
    fun updateBlueprintCheckQuestionById(
        @PathVariable questionId: UUID,
        @RequestBody request: UpdateBlueprintCheckQuestionRequest,
    ): UpdateBlueprintCheckQuestionResponse {
        return blueprintCheckQuestionService.updateBlueprintCheckQuestionById(questionId, request)
    }

    @PutMapping("/checks/question/{questionId}/position")
    fun updateBlueprintCheckQuestionPositionById(
        @PathVariable questionId: UUID,
        @RequestBody request: UpdateBlueprintCheckQuestionPositionRequest,
    ): List<UpdateBlueprintCheckQuestionPositionResponse> {
        return blueprintCheckQuestionService.updateBlueprintCheckQuestionPositionById(questionId, request)
    }

    @DeleteMapping("/checks/question/{questionId}")
    fun deleteBlueprintCheckQuestionById(
        @PathVariable questionId: UUID,
    ) {
        blueprintCheckQuestionService.deleteBlueprintCheckQuestionById(questionId)
    }
}
