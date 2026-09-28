package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintStep
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateSubGraphNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.CreateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.DeleteBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepPositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.step.UpdateBlueprintStepRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.CreateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.DeleteBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.GetBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.step.UpdateBlueprintStepResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintStepRepository
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.collections.forEach

/**
 * Manages ordered steps within a blueprint phase.
 *
 * The service enforces scoped draft access and optimistic revisions, shifts sibling positions during insertion and
 * movement, and maps entities to response DTOs. Deleting a step also removes its sub-graph relationships and reports
 * the revisions of affected nodes.
 */
@Service
class BlueprintStepService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintStepRepository: BlueprintStepRepository,
    private val entityManager: EntityManager,
    private val blueprintSubGraphNodeService: BlueprintSubGraphNodeService,
) {
    /**
     * Returns step for phase.
     *
     * Runs the repository query matching the requested scope and maps the ordered step entities to response DTOs.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @return The mapped result of the operation.
     */
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

    /**
     * Returns step by id.
     *
     * Uses the access service to enforce the ownership scope before mapping the step.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param stepId Identifier of the blueprint step.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
     */
    @Transactional(readOnly = true)
    fun getBlueprintStepById(
        scope: BlueprintScope,
        stepId: UUID,
    ): GetBlueprintStepResponse {
        return blueprintAccessService
            .getAuthorizedStep(scope, stepId)
            .toGetResponse()
    }

    /**
     * Creates step for phase.
     *
     * Requires an editable parent, validates the insertion position, shifts later siblings right, and persists the
     * new step.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid insertion position, 404 for a missing parent, or 409 when
     *   its path is not a draft.
     */
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
            graphX = request.graphX,
            graphY = request.graphY,
        )

        return blueprintStepRepository.save(blueprintStep).toCreateResponse()
    }

    /**
     * Updates step by id.
     *
     * Requires an editable step and matching revision, shifts siblings when its position changes, and persists the
     * replacement values.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param stepId Identifier of the blueprint step.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid position, 404 for a missing entity, or 409 for a stale
     *   revision or non-draft path.
     */
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

    /**
     * Updates step position by id.
     *
     * Requires an editable step and matching revision, shifts intervening siblings, and flushes every changed
     * position together.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param stepId Identifier of the blueprint step.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid position, 404 for a missing entity, or 409 for a stale
     *   revision or non-draft path.
     */
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

    /**
     * Deletes step by id.
     *
     * Requires an editable step and matching revision before deletion. Incoming and outgoing graph edges are
     * removed before deletion, and affected revisions are returned.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param stepId Identifier of the blueprint step.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 for a missing entity, or 409 for a stale revision or non-draft path.
     */
    @Transactional
    fun deleteBlueprintStepById(
        scope: BlueprintScope,
        stepId: UUID,
        request: DeleteBlueprintStepRequest,
    ): DeleteBlueprintStepResponse {
        val blueprintStep = blueprintAccessService.getAuthorizedEditableStep(scope, stepId)

        validateRevision(blueprintStep, request.revision)
        val updatedSteps = blueprintSubGraphNodeService.removeAllConnections(blueprintStep)
        blueprintStepRepository.delete(blueprintStep)
        entityManager.flush()
        return DeleteBlueprintStepResponse(updatedSteps.map { it.toUpdateSubGraphNodeResponse() })
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
}
