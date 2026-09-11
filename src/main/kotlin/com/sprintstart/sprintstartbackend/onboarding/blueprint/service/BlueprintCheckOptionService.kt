package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.CreateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.DeleteBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.CreateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.GetBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckOptionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.collections.forEach

/**
 * Manages ordered answer options within blueprint check questions.
 *
 * Scope-specific reads prevent cross-project access. Insertions and moves shift siblings to keep positions contiguous,
 * while updates and deletes require the owning blueprint to remain a draft and the supplied revision to be current.
 */
@Service
class BlueprintCheckOptionService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintCheckOptionRepository: BlueprintCheckOptionRepository,
) {
    /**
     * Returns check options for question.
     *
     * Runs the repository query matching the requested scope and maps the ordered check option entities to response
     * DTOs.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param questionId Identifier of the check question.
     * @return The mapped result of the operation.
     */
    @Transactional(readOnly = true)
    fun getBlueprintCheckOptionsForQuestion(
        scope: BlueprintScope,
        questionId: UUID,
    ): List<GetBlueprintCheckOptionResponse> {
        return when (scope) {
            is BlueprintScope.Global -> {
                blueprintCheckOptionRepository
                    .findAllByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdNullAndBlueprintCheckQuestionId(
                        questionId,
                    )
            }

            is BlueprintScope.Project -> {
                blueprintCheckOptionRepository
                    .findAllByBlueprintCheckQuestionBlueprintPhaseBlueprintPathProjectIdAndBlueprintCheckQuestionId(
                        scope.projectId,
                        questionId,
                    )
            }
        }.map { it.toGetResponse() }
    }

    /**
     * Returns check option by id.
     *
     * Uses the access service to enforce the ownership scope before mapping the check option.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param optionId Identifier of the check option.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
     */
    @Transactional(readOnly = true)
    fun getBlueprintCheckOptionById(
        scope: BlueprintScope,
        optionId: UUID,
    ): GetBlueprintCheckOptionResponse {
        return blueprintAccessService
            .getAuthorizedCheckOption(scope, optionId)
            .toGetResponse()
    }

    /**
     * Creates check option for question.
     *
     * Requires an editable parent, validates the insertion position, shifts later siblings right, and persists the
     * new check option.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param questionId Identifier of the check question.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid insertion position, 404 for a missing parent, or 409 when
     *   its path is not a draft.
     */
    @Transactional
    fun createBlueprintCheckOptionForQuestion(
        scope: BlueprintScope,
        questionId: UUID,
        request: CreateBlueprintCheckOptionRequest,
    ): CreateBlueprintCheckOptionResponse {
        val question = blueprintAccessService.getAuthorizedEditableCheckQuestion(scope, questionId)

        shiftOptionsRight(question, request)

        val option = BlueprintCheckOption(
            blueprintCheckQuestion = question,
            position = request.position,
            label = request.label,
            correct = request.correct,
        )

        return blueprintCheckOptionRepository.save(option).toCreateResponse()
    }

    /**
     * Updates check option by id.
     *
     * Requires an editable check option and matching revision, shifts siblings when its position changes, and
     * persists the replacement values.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param optionId Identifier of the check option.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid position, 404 for a missing entity, or 409 for a stale
     *   revision or non-draft path.
     */
    @Transactional
    fun updateBlueprintCheckOptionById(
        scope: BlueprintScope,
        optionId: UUID,
        request: UpdateBlueprintCheckOptionRequest,
    ): UpdateBlueprintCheckOptionResponse {
        val option = blueprintAccessService.getAuthorizedEditableCheckOption(scope, optionId)

        validateRevision(option, request.revision)

        shiftOptionsBetween(option, request.position)

        option.position = request.position
        option.label = request.label
        option.correct = request.correct

        return blueprintCheckOptionRepository.save(option).toUpdateResponse()
    }

    /**
     * Updates check option position by id.
     *
     * Requires an editable check option and matching revision, shifts intervening siblings, and flushes every
     * changed position together.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param optionId Identifier of the check option.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid position, 404 for a missing entity, or 409 for a stale
     *   revision or non-draft path.
     */
    @Transactional
    fun updateBlueprintCheckOptionPositionById(
        scope: BlueprintScope,
        optionId: UUID,
        request: UpdateBlueprintCheckOptionPositionRequest,
    ): List<UpdateBlueprintCheckOptionPositionResponse> {
        val option = blueprintAccessService.getAuthorizedEditableCheckOption(scope, optionId)

        validateRevision(option, request.revision)

        val shiftedOptions = shiftOptionsBetween(option, request.position)
        option.position = request.position
        shiftedOptions.add(option)

        blueprintCheckOptionRepository.saveAllAndFlush(shiftedOptions)

        return shiftedOptions.map { it.toUpdatePositionResponse() }
    }

    /**
     * Deletes check option by id.
     *
     * Requires an editable check option and matching revision before deletion.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param optionId Identifier of the check option.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @throws ResponseStatusException With 404 for a missing entity, or 409 for a stale revision or non-draft path.
     */
    @Transactional
    fun deleteBlueprintCheckOptionById(
        scope: BlueprintScope,
        optionId: UUID,
        request: DeleteBlueprintCheckOptionRequest,
    ) {
        val option = blueprintAccessService.getAuthorizedEditableCheckOption(scope, optionId)

        validateRevision(option, request.revision)

        blueprintCheckOptionRepository.delete(option)
    }

    // Helper Methods

    private fun validateRevision(
        option: BlueprintCheckOption,
        revision: Long,
    ) {
        if (option.revision != revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint check option has been modified by another request. Please reload and try again.",
            )
        }
    }

    private fun shiftOptionsRight(
        question: BlueprintCheckQuestion,
        request: CreateBlueprintCheckOptionRequest,
    ) {
        val optionCount = blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(question.id)

        if (request.position !in 0..optionCount) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Position must be between 0 and $optionCount",
            )
        }

        val stepsToShift = blueprintCheckOptionRepository
            .findAllByBlueprintCheckQuestionIdAndPositionGreaterThanEqualOrderByPositionDesc(
                question.id,
                request.position,
            )

        stepsToShift.forEach { it.position += 1 }
    }

    private fun shiftOptionsBetween(
        option: BlueprintCheckOption,
        newPosition: Int,
    ): MutableList<BlueprintCheckOption> {
        val stepCount = blueprintCheckOptionRepository.countByBlueprintCheckQuestionId(option.blueprintCheckQuestion.id)

        if (newPosition !in 0 until stepCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${stepCount - 1}")
        }

        val oldPosition = option.position
        var optionsToShift: MutableList<BlueprintCheckOption> = mutableListOf()

        if (oldPosition < newPosition) {
            optionsToShift = blueprintCheckOptionRepository
                .findAllByBlueprintCheckQuestionIdAndPositionBetween(
                    option.blueprintCheckQuestion.id,
                    oldPosition + 1,
                    newPosition,
                )

            optionsToShift.forEach { it.position -= 1 }
        }

        if (oldPosition > newPosition) {
            optionsToShift = blueprintCheckOptionRepository
                .findAllByBlueprintCheckQuestionIdAndPositionBetween(
                    option.blueprintCheckQuestion.id,
                    newPosition,
                    oldPosition - 1,
                )

            optionsToShift.forEach { it.position += 1 }
        }

        return optionsToShift
    }
}
