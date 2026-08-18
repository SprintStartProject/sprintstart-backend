package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckOption
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintCheckQuestion
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.CreateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.checkoption.UpdateBlueprintCheckOptionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.CreateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.GetBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.checkoption.UpdateBlueprintCheckOptionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckOptionRepository
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintCheckQuestionRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.collections.forEach

@Service
class BlueprintCheckOptionService(
    private val blueprintCheckOptionRepository: BlueprintCheckOptionRepository,
    private val blueprintCheckQuestionRepository: BlueprintCheckQuestionRepository,
) {
    @Transactional(readOnly = true)
    fun getBlueprintCheckOptionsForQuestion(questionId: UUID): List<GetBlueprintCheckOptionResponse> {
        return blueprintCheckOptionRepository
            .findAllByBlueprintCheckQuestionId(questionId)
            .map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintCheckOptionById(optionId: UUID): GetBlueprintCheckOptionResponse {
        return blueprintCheckOptionRepository
            .findById(optionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Option not found with id: $optionId") }
            .toGetResponse()
    }

    @Transactional
    fun createBlueprintCheckOptionForQuestion(
        questionId: UUID,
        request: CreateBlueprintCheckOptionRequest,
    ): CreateBlueprintCheckOptionResponse {
        val question = blueprintCheckQuestionRepository
            .findById(questionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found with id: $questionId") }

        if (question.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

        shiftOptionsRight(question, request)

        val option = BlueprintCheckOption(
            blueprintCheckQuestion = question,
            position = request.position,
            label = request.label,
            correct = request.correct,
        )

        return blueprintCheckOptionRepository.save(option).toCreateResponse()
    }

    @Transactional
    fun updateBlueprintCheckOptionById(
        optionId: UUID,
        request: UpdateBlueprintCheckOptionRequest,
    ): UpdateBlueprintCheckOptionResponse {
        val option = blueprintCheckOptionRepository
            .findById(optionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Option not found with id: $optionId") }

        if (option.blueprintCheckQuestion.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

        if (option.revision != request.revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint check option has been modified by another request. Please reload and try again.",
            )
        }

        shiftOptionsBetween(option, request.position)

        option.position = request.position
        option.label = request.label
        option.correct = request.correct

        return blueprintCheckOptionRepository.save(option).toUpdateResponse()
    }

    @Transactional
    fun updateBlueprintCheckOptionPositionById(
        optionId: UUID,
        request: UpdateBlueprintCheckOptionPositionRequest,
    ): List<UpdateBlueprintCheckOptionPositionResponse> {
        val option = blueprintCheckOptionRepository
            .findById(optionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Option not found with id: $optionId") }

        if (option.blueprintCheckQuestion.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

        if (option.revision != request.revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint check option has been modified by another request. Please reload and try again.",
            )
        }

        val shiftedOptions = shiftOptionsBetween(option, request.position)
        option.position = request.position
        shiftedOptions.add(option)

        blueprintCheckOptionRepository.saveAllAndFlush(shiftedOptions)

        return shiftedOptions.map { it.toUpdatePositionResponse() }
    }

    @Transactional
    fun deleteBlueprintCheckOptionById(
        optionId: UUID,
    ) {
        val option = blueprintCheckOptionRepository
            .findById(optionId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Option not found with id: $optionId") }

        if (option.blueprintCheckQuestion.blueprintPhase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

        blueprintCheckOptionRepository.delete(option)
    }

    // Helper Methods

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
