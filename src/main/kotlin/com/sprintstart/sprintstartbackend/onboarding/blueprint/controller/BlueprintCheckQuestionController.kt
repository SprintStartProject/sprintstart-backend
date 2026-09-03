package com.sprintstart.sprintstartbackend.onboarding.blueprint.controller

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.CreateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.DeleteBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.CreateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.GetBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.service.BlueprintCheckQuestionService
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
@RequestMapping("/api/v1/onboarding/blueprints/")
class BlueprintCheckQuestionAdminController(
    private val blueprintCheckQuestionService: BlueprintCheckQuestionService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/phase/{phaseId}/checks/questions")
    fun getBlueprintCheckQuestionsForPhase(
        @PathVariable phaseId: UUID,
    ): List<GetBlueprintCheckQuestionResponse> {
        return blueprintCheckQuestionService.getBlueprintCheckQuestionsForPhase(BlueprintScope.Global, phaseId)
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/checks/questions/{questionId}")
    fun getBlueprintCheckQuestionById(
        @PathVariable questionId: UUID,
    ): GetBlueprintCheckQuestionResponse {
        return blueprintCheckQuestionService.getBlueprintCheckQuestionById(BlueprintScope.Global, questionId)
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/phase/{phaseId}/checks/questions")
    fun createBlueprintCheckQuestionForPhase(
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintCheckQuestionRequest,
    ): CreateBlueprintCheckQuestionResponse {
        return blueprintCheckQuestionService.createBlueprintCheckQuestionForPhase(
            BlueprintScope.Global,
            phaseId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/checks/question/{questionId}")
    fun updateBlueprintCheckQuestionById(
        @PathVariable questionId: UUID,
        @RequestBody request: UpdateBlueprintCheckQuestionRequest,
    ): UpdateBlueprintCheckQuestionResponse {
        return blueprintCheckQuestionService.updateBlueprintCheckQuestionById(
            BlueprintScope.Global,
            questionId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/checks/question/{questionId}/position")
    fun updateBlueprintCheckQuestionPositionById(
        @PathVariable questionId: UUID,
        @RequestBody request: UpdateBlueprintCheckQuestionPositionRequest,
    ): List<UpdateBlueprintCheckQuestionPositionResponse> {
        return blueprintCheckQuestionService.updateBlueprintCheckQuestionPositionById(
            BlueprintScope.Global,
            questionId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/checks/question/{questionId}")
    fun deleteBlueprintCheckQuestionById(
        @PathVariable questionId: UUID,
        @RequestBody request: DeleteBlueprintCheckQuestionRequest,
    ) {
        blueprintCheckQuestionService.deleteBlueprintCheckQuestionById(
            BlueprintScope.Global,
            questionId,
            request,
        )
    }
}

@RestController
@RequestMapping("/api/v1/projects/{projectId}/onboarding/blueprints/")
class BlueprintCheckQuestionController(
    private val blueprintCheckQuestionService: BlueprintCheckQuestionService,
) {
    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @GetMapping("/phase/{phaseId}/checks/questions")
    fun getBlueprintCheckQuestionsForPhase(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
    ): List<GetBlueprintCheckQuestionResponse> {
        return blueprintCheckQuestionService.getBlueprintCheckQuestionsForPhase(
            BlueprintScope.Project(projectId),
            phaseId,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @GetMapping("/checks/questions/{questionId}")
    fun getBlueprintCheckQuestionById(
        @PathVariable projectId: UUID,
        @PathVariable questionId: UUID,
    ): GetBlueprintCheckQuestionResponse {
        return blueprintCheckQuestionService.getBlueprintCheckQuestionById(
            BlueprintScope.Project(projectId),
            questionId,
        )
    }

    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PostMapping("/phase/{phaseId}/checks/questions")
    fun createBlueprintCheckQuestionForPhase(
        @PathVariable projectId: UUID,
        @PathVariable phaseId: UUID,
        @RequestBody request: CreateBlueprintCheckQuestionRequest,
    ): CreateBlueprintCheckQuestionResponse {
        return blueprintCheckQuestionService.createBlueprintCheckQuestionForPhase(
            BlueprintScope.Project(projectId),
            phaseId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PutMapping("/checks/question/{questionId}")
    fun updateBlueprintCheckQuestionById(
        @PathVariable projectId: UUID,
        @PathVariable questionId: UUID,
        @RequestBody request: UpdateBlueprintCheckQuestionRequest,
    ): UpdateBlueprintCheckQuestionResponse {
        return blueprintCheckQuestionService.updateBlueprintCheckQuestionById(
            BlueprintScope.Project(projectId),
            questionId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.OK)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @PutMapping("/checks/question/{questionId}/position")
    fun updateBlueprintCheckQuestionPositionById(
        @PathVariable projectId: UUID,
        @PathVariable questionId: UUID,
        @RequestBody request: UpdateBlueprintCheckQuestionPositionRequest,
    ): List<UpdateBlueprintCheckQuestionPositionResponse> {
        return blueprintCheckQuestionService.updateBlueprintCheckQuestionPositionById(
            BlueprintScope.Project(projectId),
            questionId,
            request,
        )
    }

    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','PM','HR')")
    @DeleteMapping("/checks/question/{questionId}")
    fun deleteBlueprintCheckQuestionById(
        @PathVariable projectId: UUID,
        @PathVariable questionId: UUID,
        @RequestBody request: DeleteBlueprintCheckQuestionRequest,
    ) {
        blueprintCheckQuestionService.deleteBlueprintCheckQuestionById(
            BlueprintScope.Project(projectId),
            questionId,
            request,
        )
    }
}
