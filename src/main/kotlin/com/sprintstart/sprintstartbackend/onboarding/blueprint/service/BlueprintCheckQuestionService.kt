package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateSubGraphNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.CreateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.DeleteBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkquestion.UpdateBlueprintCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.CreateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.DeleteBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.GetBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkquestion.UpdateBlueprintCheckQuestionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckQuestionRepository
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.collections.forEach
import kotlin.ranges.contains

/**
 * Manages ordered assessment questions within blueprint phases.
 *
 * The service maintains sibling positions, requires answers for short-text questions, and applies scoped draft and
 * revision checks to mutations. Because questions participate in the step sub-graph, deletion also removes every
 * incoming and outgoing blocker relationship.
 */
@Service
class BlueprintCheckQuestionService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintCheckQuestionRepository: BlueprintCheckQuestionRepository,
    private val blueprintSubGraphNodeService: BlueprintSubGraphNodeService,
    private val entityManager: EntityManager,
) {
    /**
     * Returns check questions for phase.
     *
     * Runs the repository query matching the requested scope and maps the ordered check question entities to
     * response DTOs.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @return The mapped result of the operation.
     */
    @Transactional(readOnly = true)
    fun getBlueprintCheckQuestionsForPhase(
        scope: BlueprintScope,
        phaseId: UUID,
    ): List<GetBlueprintCheckQuestionResponse> {
        return when (scope) {
            is BlueprintScope.Global -> {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintPhaseId(phaseId)
            }

            is BlueprintScope.Project -> {
                blueprintCheckQuestionRepository
                    .findAllByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(scope.projectId, phaseId)
            }
        }.map { it.toGetResponse() }
    }

    /**
     * Returns check question by id.
     *
     * Uses the access service to enforce the ownership scope before mapping the check question.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param questionId Identifier of the check question.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
     */
    @Transactional(readOnly = true)
    fun getBlueprintCheckQuestionById(
        scope: BlueprintScope,
        questionId: UUID,
    ): GetBlueprintCheckQuestionResponse {
        return blueprintAccessService
            .getAuthorizedCheckQuestion(scope, questionId)
            .toGetResponse()
    }

    /**
     * Creates check question for phase.
     *
     * Requires an editable parent, validates the insertion position, shifts later siblings right, and persists the
     * new check question. It also requires a correct answer for SHORT_TEXT questions.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid insertion position or missing short-text answer,
     *   404 for a missing parent, or 409 when its path is not a draft.
     */
    @Transactional
    fun createBlueprintCheckQuestionForPhase(
        scope: BlueprintScope,
        phaseId: UUID,
        request: CreateBlueprintCheckQuestionRequest,
    ): CreateBlueprintCheckQuestionResponse {
        if (request.type == CheckQuestionType.SHORT_TEXT && request.correctAnswer == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Correct answer must be set for question type: Short Text",
            )
        }

        val phase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)

        shiftQuestionsRight(phase, request)

        val question = BlueprintCheckQuestion(
            blueprintPhase = phase,
            title = request.title,
            position = request.position,
            type = request.type,
            question = request.question,
            explanation = request.explanation,
            correctAnswer = request.correctAnswer,
            graphX = request.graphX,
            graphY = request.graphY,
        )

        return blueprintCheckQuestionRepository.save(question).toCreateResponse()
    }

    /**
     * Updates check question by id.
     *
     * Requires an editable check question and matching revision, shifts siblings when its position changes, and
     * persists the replacement values. A SHORT_TEXT question must retain a correct answer.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param questionId Identifier of the check question.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid position, 404 for a missing entity, or 409 for a stale
     *   revision or non-draft path.
     */
    @Transactional
    fun updateBlueprintCheckQuestionById(
        scope: BlueprintScope,
        questionId: UUID,
        request: UpdateBlueprintCheckQuestionRequest,
    ): UpdateBlueprintCheckQuestionResponse {
        if (request.type == CheckQuestionType.SHORT_TEXT && request.correctAnswer == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Correct answer must be set for question type: Short Text",
            )
        }

        val question = blueprintAccessService.getAuthorizedEditableCheckQuestion(scope, questionId)

        validateRevision(question, request.revision)

        shiftQuestionsBetween(question, request.position)

        question.title = request.title
        question.position = request.position
        question.type = request.type
        question.question = request.question
        question.explanation = request.explanation
        question.correctAnswer = request.correctAnswer

        return blueprintCheckQuestionRepository.save(question).toUpdateResponse()
    }

    /**
     * Updates check question position by id.
     *
     * Requires an editable check question and matching revision, shifts intervening siblings, and flushes every
     * changed position together.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param questionId Identifier of the check question.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid position, 404 for a missing entity, or 409 for a stale
     *   revision or non-draft path.
     */
    @Transactional
    fun updateBlueprintCheckQuestionPositionById(
        scope: BlueprintScope,
        questionId: UUID,
        request: UpdateBlueprintCheckQuestionPositionRequest,
    ): List<UpdateBlueprintCheckQuestionPositionResponse> {
        val question = blueprintAccessService.getAuthorizedEditableCheckQuestion(scope, questionId)

        validateRevision(question, request.revision)

        val shiftedQuestions = shiftQuestionsBetween(question, request.position)
        question.position = request.position
        shiftedQuestions.add(question)
        blueprintCheckQuestionRepository.saveAllAndFlush(shiftedQuestions)

        return shiftedQuestions.map { it.toUpdatePositionResponse() }
    }

    /**
     * Deletes check question by id.
     *
     * Requires an editable check question and matching revision before deletion. Incoming and outgoing graph edges
     * are removed before deletion, and affected revisions are returned.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param questionId Identifier of the check question.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 for a missing entity, or 409 for a stale revision or non-draft path.
     */
    @Transactional
    fun deleteBlueprintCheckQuestionById(
        scope: BlueprintScope,
        questionId: UUID,
        request: DeleteBlueprintCheckQuestionRequest,
    ): DeleteBlueprintCheckQuestionResponse {
        val question = blueprintAccessService.getAuthorizedEditableCheckQuestion(scope, questionId)

        validateRevision(question, request.revision)
        val changedQuestions = blueprintSubGraphNodeService.removeAllConnections(question)
        blueprintCheckQuestionRepository.delete(question)
        entityManager.flush()
        return DeleteBlueprintCheckQuestionResponse(
            changedQuestions.map { it.toUpdateSubGraphNodeResponse() },
        )
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
