package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.CreateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.CreateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.GetBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPhaseRepository
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
    private val blueprintCheckQuestionRepository: BlueprintCheckQuestionRepository,
    private val blueprintPhaseRepository: BlueprintPhaseRepository,
) {
    @Transactional(readOnly = true)
    fun getBlueprintCheckQuestionsForPhase(
        phaseId: UUID,
    ): List<GetBlueprintCheckQuestionResponse> {
        return blueprintCheckQuestionRepository
            .findAllByBlueprintPhaseId(phaseId)
            .map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintCheckQuestionById(
        questionId: UUID,
    ): GetBlueprintCheckQuestionResponse {
        return blueprintCheckQuestionRepository
            .findById(questionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found with id: $questionId") }
            .toGetResponse()
    }

    @Transactional
    fun createBlueprintCheckQuestionForPhase(
        phaseId: UUID,
        request: CreateBlueprintCheckQuestionRequest,
    ): CreateBlueprintCheckQuestionResponse {
        if (request.type == CheckQuestionType.SHORT_TEXT && request.correctAnswer == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Correct answer must be set for question type: Short Text",
            )
        }

        val phase = blueprintPhaseRepository
            .findById(phaseId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Phase not found with id: $phaseId") }

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
        questionId: UUID,
        request: UpdateBlueprintCheckQuestionRequest,
    ): UpdateBlueprintCheckQuestionResponse {
        if (request.type == CheckQuestionType.SHORT_TEXT && request.correctAnswer == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Correct answer must be set for question type: Short Text",
            )
        }

        val question = blueprintCheckQuestionRepository
            .findById(questionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found with id: $questionId") }

        if (question.revision != request.revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint check question has been modified by another request. Please reload and try again.",
            )
        }

        shiftQuestionsBetween(question, request)

        question.position = request.position
        question.type = request.type
        question.question = request.question
        question.explanation = request.explanation
        question.correctAnswer = request.correctAnswer

        return blueprintCheckQuestionRepository.save(question).toUpdateResponse()
    }

    @Transactional
    fun deleteBlueprintCheckQuestionById(
        questionId: UUID,
    ) {
        val question = blueprintCheckQuestionRepository
            .findById(questionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found with id: $questionId") }

        blueprintCheckQuestionRepository.delete(question)
    }

    // Helper Methods

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
        request: UpdateBlueprintCheckQuestionRequest,
    ) {
        val questionCount = blueprintCheckQuestionRepository.countByBlueprintPhaseId(question.blueprintPhase.id)

        if (request.position !in 0 until questionCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${questionCount - 1}")
        }

        val oldPosition = question.position
        val newPosition = request.position
        var questionsToShift: MutableList<BlueprintCheckQuestion>

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
    }
}
