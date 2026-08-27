package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.CreateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.DeleteBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.CreateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.GetBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.collections.forEach
import kotlin.ranges.contains

@Service
class BlueprintCheckQuestionService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintCheckQuestionRepository: BlueprintCheckQuestionRepository,
) {
    @Transactional(readOnly = true)
    fun getBlueprintCheckQuestionsForPhase(
        projectId: UUID,
        phaseId: UUID,
    ): List<GetBlueprintCheckQuestionResponse> {
        return blueprintCheckQuestionRepository
            .findAllByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(projectId, phaseId)
            .map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintCheckQuestionById(
        projectId: UUID,
        questionId: UUID,
    ): GetBlueprintCheckQuestionResponse {
        return blueprintAccessService
            .getAuthorizedCheckQuestion(projectId, questionId)
            .toGetResponse()
    }

    @Transactional
    fun createBlueprintCheckQuestionForPhase(
        projectId: UUID,
        phaseId: UUID,
        request: CreateBlueprintCheckQuestionRequest,
    ): CreateBlueprintCheckQuestionResponse {
        if (request.type == CheckQuestionType.SHORT_TEXT && request.correctAnswer == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Correct answer must be set for question type: Short Text",
            )
        }

        val phase = blueprintAccessService.getAuthorizedEditablePhase(projectId, phaseId)

        shiftQuestionsRight(phase, request)

        val question = BlueprintCheckQuestion(
            blueprintPhase = phase,
            position = request.position,
            type = request.type,
            question = request.question,
            explanation = request.explanation,
            correctAnswer = request.correctAnswer,
        )

        return blueprintCheckQuestionRepository.save(question).toCreateResponse()
    }

    @Transactional
    fun updateBlueprintCheckQuestionById(
        projectId: UUID,
        questionId: UUID,
        request: UpdateBlueprintCheckQuestionRequest,
    ): UpdateBlueprintCheckQuestionResponse {
        if (request.type == CheckQuestionType.SHORT_TEXT && request.correctAnswer == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Correct answer must be set for question type: Short Text",
            )
        }

        val question = blueprintAccessService.getAuthorizedEditableCheckQuestion(projectId, questionId)

        validateRevision(question, request.revision)

        shiftQuestionsBetween(question, request.position)

        question.position = request.position
        question.type = request.type
        question.question = request.question
        question.explanation = request.explanation
        question.correctAnswer = request.correctAnswer

        return blueprintCheckQuestionRepository.save(question).toUpdateResponse()
    }

    @Transactional
    fun updateBlueprintCheckQuestionPositionById(
        projectId: UUID,
        questionId: UUID,
        request: UpdateBlueprintCheckQuestionPositionRequest,
    ): List<UpdateBlueprintCheckQuestionPositionResponse> {
        val question = blueprintAccessService.getAuthorizedEditableCheckQuestion(projectId, questionId)

        validateRevision(question, request.revision)

        val shiftedQuestions = shiftQuestionsBetween(question, request.position)
        question.position = request.position
        shiftedQuestions.add(question)
        blueprintCheckQuestionRepository.saveAllAndFlush(shiftedQuestions)

        return shiftedQuestions.map { it.toUpdatePositionResponse() }
    }

    @Transactional
    fun deleteBlueprintCheckQuestionById(
        projectId: UUID,
        questionId: UUID,
        request: DeleteBlueprintCheckQuestionRequest,
    ) {
        val question = blueprintAccessService.getAuthorizedEditableCheckQuestion(projectId, questionId)

        validateRevision(question, request.revision)

        blueprintCheckQuestionRepository.delete(question)
    }

    // Helper Methods

    private fun validateRevision(
        question: BlueprintCheckQuestion,
        revision: Long,
    ) {
        if (question.revision != revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint check question has been modified by another request. Please reload and try again.",
            )
        }
    }

    private fun shiftQuestionsRight(
        phase: BlueprintPhase,
        request: CreateBlueprintCheckQuestionRequest,
    ) {
        val stepCount = blueprintCheckQuestionRepository.countByBlueprintPhaseId(phase.id)

        if (request.position !in 0..stepCount) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Position must be between 0 and $stepCount",
            )
        }

        val questionsToShift = blueprintCheckQuestionRepository
            .findAllByBlueprintPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(
                phase.id,
                request.position,
            )

        questionsToShift.forEach { it.position += 1 }
    }

    private fun shiftQuestionsBetween(
        question: BlueprintCheckQuestion,
        newPosition: Int,
    ): MutableList<BlueprintCheckQuestion> {
        val questionCount = blueprintCheckQuestionRepository.countByBlueprintPhaseId(question.blueprintPhase.id)

        if (newPosition !in 0 until questionCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${questionCount - 1}")
        }

        val oldPosition = question.position
        var questionsToShift: MutableList<BlueprintCheckQuestion> = mutableListOf()

        if (oldPosition < newPosition) {
            questionsToShift = blueprintCheckQuestionRepository
                .findAllByBlueprintPhaseIdAndPositionBetween(
                    question.blueprintPhase.id,
                    oldPosition + 1,
                    newPosition,
                )

            questionsToShift.forEach { it.position -= 1 }
        }

        if (oldPosition > newPosition) {
            questionsToShift = blueprintCheckQuestionRepository
                .findAllByBlueprintPhaseIdAndPositionBetween(
                    question.blueprintPhase.id,
                    newPosition,
                    oldPosition - 1,
                )

            questionsToShift.forEach { it.position += 1 }
        }

        return questionsToShift
    }
}
