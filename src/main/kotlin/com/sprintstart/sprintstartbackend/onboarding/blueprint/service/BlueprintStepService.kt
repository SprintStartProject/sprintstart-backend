package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.AddBlueprintStepBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.CreateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.DeleteBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.RemoveBlueprintStepBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.RemoveBlueprintStepGraphPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepGraphPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.AddBlueprintStepBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.CreateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.GetBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.RemoveBlueprintStepBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.RemoveBlueprintStepGraphPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepGraphPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintStepRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.collections.forEach

@Service
class BlueprintStepService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintStepRepository: BlueprintStepRepository,
    private val entityManager: EntityManager,
) {
    @Transactional(readOnly = true)
    fun getBlueprintStepForPhase(
        scope: BlueprintScope,
        phaseId: UUID,
    ): List<GetBlueprintStepResponse> {
        return when (scope) {
            is BlueprintScope.Global -> {
                blueprintStepRepository
                    .findAllByBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintPhaseId(phaseId)
            }

            is BlueprintScope.Project -> {
                blueprintStepRepository
                    .findAllByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(scope.projectId, phaseId)
            }
        }.map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    fun getBlueprintStepById(
        scope: BlueprintScope,
        stepId: UUID,
    ): GetBlueprintStepResponse {
        return blueprintAccessService
            .getAuthorizedStep(scope, stepId)
            .toGetResponse()
    }

    @Transactional
    fun createBlueprintStepForPhase(
        scope: BlueprintScope,
        phaseId: UUID,
        request: CreateBlueprintStepRequest,
    ): CreateBlueprintStepResponse {
        val blueprintPhase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)

        shiftStepsRight(blueprintPhase, request)

        val blueprintStep = BlueprintStep(
            blueprintPhase = blueprintPhase,
            position = request.position,
            title = request.title,
            description = request.description,
            type = request.type,
            aiAssisted = false,
            estimatedMinutes = request.estimatedMinutes,
            expectedOutcome = request.expectedOutcome,
        )

        return blueprintStepRepository.save(blueprintStep).toCreateResponse()
    }

    @Transactional
    fun addBlueprintStepBlocker(
        scope: BlueprintScope,
        stepId: UUID,
        blockerId: UUID,
        request: AddBlueprintStepBlockerRequest,
    ): AddBlueprintStepBlockerResponse {
        val step = blueprintAccessService.getAuthorizedEditableStep(scope, stepId)
        val blocker = blueprintAccessService.getAuthorizedEditableStep(scope, blockerId)
        validateRevision(step, request.revision)

        if (step.blockedBy.map { it.id }.contains(blockerId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cant be blocked by an existing blocker")
        }

        // Adding step -> blocker is valid only when blocker does not already depend on step.
        dfs(blocker, step)

        markStepModified(step)
        step.blockedBy.add(blocker)
        entityManager.flush()

        return AddBlueprintStepBlockerResponse(
            step.revision + 1,
            step.blockedBy.map { it.id }.toSet(),
        )
    }

    @Transactional
    fun updateBlueprintStepById(
        scope: BlueprintScope,
        stepId: UUID,
        request: UpdateBlueprintStepRequest,
    ): UpdateBlueprintStepResponse {
        val blueprintStep = blueprintAccessService.getAuthorizedEditableStep(scope, stepId)

        validateRevision(blueprintStep, request.revision)

        shiftStepsBetween(blueprintStep, request.position)

        blueprintStep.position = request.position
        blueprintStep.title = request.title
        blueprintStep.description = request.description
        blueprintStep.type = request.type
        blueprintStep.aiAssisted = request.aiAssisted
        blueprintStep.estimatedMinutes = request.estimatedMinutes
        blueprintStep.expectedOutcome = request.expectedOutcome

        return blueprintStepRepository.save(blueprintStep).toUpdateResponse()
    }

    @Transactional
    fun updateBlueprintStepGraphPositionById(
        scope: BlueprintScope,
        stepId: UUID,
        request: UpdateBlueprintStepGraphPositionRequest,
    ): UpdateBlueprintStepGraphPositionResponse {
        val step = blueprintAccessService.getAuthorizedEditableStep(scope, stepId)
        validateRevision(step, request.revision)

        step.graphX = request.graphX
        step.graphY = request.graphY
        entityManager.flush()

        return step.toUpdateGraphResponse()
    }

    @Transactional
    fun updateBlueprintStepPositionById(
        scope: BlueprintScope,
        stepId: UUID,
        request: UpdateBlueprintStepPositionRequest,
    ): List<UpdateBlueprintStepPositionResponse> {
        val blueprintStep = blueprintAccessService.getAuthorizedEditableStep(scope, stepId)

        validateRevision(blueprintStep, request.revision)

        val shiftedSteps = shiftStepsBetween(blueprintStep, request.position)
        blueprintStep.position = request.position
        shiftedSteps.add(blueprintStep)
        blueprintStepRepository.saveAllAndFlush(shiftedSteps)

        return shiftedSteps.map { it.toUpdatePositionResponse() }
    }

    @Transactional
    fun removeBlueprintStepBlocker(
        scope: BlueprintScope,
        step: UUID,
        blockerId: UUID,
        request: RemoveBlueprintStepBlockerRequest,
    ): RemoveBlueprintStepBlockerResponse {
        val step = blueprintAccessService.getAuthorizedEditableStep(scope, step)
        val blocker = blueprintAccessService.getAuthorizedEditableStep(scope, blockerId)

        validateRevision(step, request.revision)

        markStepModified(step)
        step.blockedBy.remove(blocker)
        entityManager.flush()

        return RemoveBlueprintStepBlockerResponse(
            step.revision + 1,
            step.blockedBy.map { it.id }.toSet(),
        )
    }

    @Transactional
    fun removeBlueprintStepGraphPositionById(
        scope: BlueprintScope,
        stepId: UUID,
        request: RemoveBlueprintStepGraphPositionRequest,
    ): RemoveBlueprintStepGraphPositionResponse {
        val step = blueprintAccessService.getAuthorizedEditableStep(scope, stepId)
        validateRevision(step, request.revision)

        step.graphX = null
        step.graphY = null
        step.blockedBy.clear()
        entityManager.flush()

        return RemoveBlueprintStepGraphPositionResponse(
            step.revision,
        )
    }

    @Transactional
    fun deleteBlueprintStepById(
        scope: BlueprintScope,
        stepId: UUID,
        request: DeleteBlueprintStepRequest,
    ) {
        val blueprintStep = blueprintAccessService.getAuthorizedEditableStep(scope, stepId)

        validateRevision(blueprintStep, request.revision)

        blueprintStepRepository.delete(blueprintStep)
    }

    // Helper Methods

    private fun validateRevision(
        step: BlueprintStep,
        revision: Long,
    ) {
        if (step.revision != revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint step has been modified by another request. Please reload and try again.",
            )
        }
    }

    private fun shiftStepsRight(
        phase: BlueprintPhase,
        request: CreateBlueprintStepRequest,
    ) {
        val stepCount = blueprintStepRepository.countByBlueprintPhaseId(phase.id)

        if (request.position !in 0..stepCount) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Position must be between 0 and $stepCount",
            )
        }

        val stepsToShift = blueprintStepRepository
            .findAllByBlueprintPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(
                phase.id,
                request.position,
            )

        stepsToShift.forEach { it.position += 1 }
    }

    private fun shiftStepsBetween(
        step: BlueprintStep,
        newPosition: Int,
    ): MutableList<BlueprintStep> {
        val stepCount = blueprintStepRepository.countByBlueprintPhaseId(step.blueprintPhase.id)

        if (newPosition !in 0 until stepCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${stepCount - 1}")
        }

        val oldPosition = step.position
        var stepsToShift: MutableList<BlueprintStep> = mutableListOf()

        if (oldPosition < newPosition) {
            stepsToShift = blueprintStepRepository
                .findAllByBlueprintPhaseIdAndPositionBetween(
                    step.blueprintPhase.id,
                    oldPosition + 1,
                    newPosition,
                )

            stepsToShift.forEach { it.position -= 1 }
        }

        if (oldPosition > newPosition) {
            stepsToShift = blueprintStepRepository
                .findAllByBlueprintPhaseIdAndPositionBetween(
                    step.blueprintPhase.id,
                    newPosition,
                    oldPosition - 1,
                )

            stepsToShift.forEach { it.position += 1 }
        }

        return stepsToShift
    }

    private fun dfs(currentStep: BlueprintStep, targetStep: BlueprintStep) {
        if (currentStep == targetStep) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Blocking cant be cyclic")
        }

        for (step in currentStep.blockedBy) {
            dfs(step, targetStep)
        }
    }

    private fun markStepModified(step: BlueprintStep) {
        entityManager.lock(
            step,
            LockModeType.OPTIMISTIC_FORCE_INCREMENT,
        )
    }
}
