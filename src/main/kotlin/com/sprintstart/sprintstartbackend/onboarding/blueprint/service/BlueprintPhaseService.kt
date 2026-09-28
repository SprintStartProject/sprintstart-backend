package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPath
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateGraphNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.CreateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.DeleteBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhasePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.UpdateBlueprintPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.DeleteBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.GetBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhasePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.UpdateBlueprintPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPhaseRepository
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.ranges.contains

/**
 * Manages ordered phases within a scoped blueprint path.
 *
 * Insertions and moves keep phase positions contiguous by shifting affected siblings. Mutations require a draft path
 * and matching optimistic revision. Deletion also removes graph edges so surviving phases cannot reference the deleted
 * phase.
 */
@Service
class BlueprintPhaseService(
    private val blueprintAccessService: BlueprintAccessService,
    private val blueprintPhaseRepository: BlueprintPhaseRepository,
    private val blueprintGraphNodeService: BlueprintGraphNodeService,
    private val entityManager: EntityManager,
) {
    /**
     * Returns phases for path.
     *
     * Runs the repository query matching the requested scope and maps the ordered phase entities to response DTOs.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param pathId Identifier of the blueprint path.
     * @return The mapped result of the operation.
     */
    @Transactional(readOnly = true)
    fun getBlueprintPhasesForPath(
        scope: BlueprintScope,
        pathId: UUID,
    ): List<GetBlueprintPhaseResponse> {
        return when (scope) {
            is BlueprintScope.Global -> {
                blueprintPhaseRepository
                    .findAllByBlueprintPathProjectIdIsNullAndBlueprintPathId(pathId)
            }

            is BlueprintScope.Project -> {
                blueprintPhaseRepository
                    .findAllByBlueprintPathProjectIdAndBlueprintPathId(scope.projectId, pathId)
            }
        }.map { it.toGetResponse() }
    }

    /**
     * Returns phase by id.
     *
     * Uses the access service to enforce the ownership scope before mapping the phase.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 when the entity does not exist in the requested scope.
     */
    @Transactional(readOnly = true)
    fun getBlueprintPhaseById(
        scope: BlueprintScope,
        phaseId: UUID,
    ): GetBlueprintPhaseResponse {
        return blueprintAccessService
            .getAuthorizedPhase(scope, phaseId)
            .toGetResponse()
    }

    /**
     * Creates phase for path.
     *
     * Requires an editable parent, validates the insertion position, shifts later siblings right, and persists the
     * new phase.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param pathId Identifier of the blueprint path.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid insertion position, 404 for a missing parent, or 409 when
     *   its path is not a draft.
     */
    @Transactional
    fun createBlueprintPhaseForPath(
        scope: BlueprintScope,
        pathId: UUID,
        request: CreateBlueprintPhaseRequest,
    ): CreateBlueprintPhaseResponse {
        val path = blueprintAccessService.getAuthorizedDraftPath(scope, pathId)

        shiftPhasesRight(path, request)

        val phase = BlueprintPhase(
            blueprintPath = path,
            position = request.position,
            title = request.title,
            description = request.description,
            aiPrompt = request.aiPrompt,
            type = request.type,
            graphX = request.graphX,
            graphY = request.graphY,
        )

        return blueprintPhaseRepository.save(phase).toCreateResponse()
    }

    /**
     * Updates phase by id.
     *
     * Requires an editable phase and matching revision, shifts siblings when its position changes, and persists the
     * replacement values.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid position, 404 for a missing entity, or 409 for a stale
     *   revision or non-draft path.
     */
    @Transactional
    fun updateBlueprintPhaseById(
        scope: BlueprintScope,
        phaseId: UUID,
        request: UpdateBlueprintPhaseRequest,
    ): UpdateBlueprintPhaseResponse {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)

        validateRevision(phase, request.revision)

        shiftPhasesBetween(phase, request.position)

        phase.position = request.position
        phase.title = request.title
        phase.description = request.description
        phase.aiPrompt = request.aiPrompt
        phase.type = request.type

        return blueprintPhaseRepository.save(phase).toUpdateResponse()
    }

    /**
     * Updates phase position by id.
     *
     * Requires an editable phase and matching revision, shifts intervening siblings, and flushes every changed
     * position together.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for an invalid position, 404 for a missing entity, or 409 for a stale
     *   revision or non-draft path.
     */
    @Transactional
    fun updateBlueprintPhasePositionById(
        scope: BlueprintScope,
        phaseId: UUID,
        request: UpdateBlueprintPhasePositionRequest,
    ): List<UpdateBlueprintPhasePositionResponse> {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)

        validateRevision(phase, request.revision)

        val shiftedPhases = shiftPhasesBetween(phase, request.position)
        phase.position = request.position
        shiftedPhases.add(phase)
        blueprintPhaseRepository.saveAllAndFlush(shiftedPhases)

        return shiftedPhases.map { it.toUpdatePositionResponse() }
    }

    /**
     * Deletes phase by id.
     *
     * Requires an editable phase and matching revision before deletion. Incoming and outgoing graph edges are
     * removed before deletion, and affected revisions are returned.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 for a missing entity, or 409 for a stale revision or non-draft path.
     */
    @Transactional
    fun deleteBlueprintPhaseById(
        scope: BlueprintScope,
        phaseId: UUID,
        request: DeleteBlueprintPhaseRequest,
    ): DeleteBlueprintPhaseResponse {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)

        validateRevision(phase, request.revision)

        val updatedPhases = blueprintGraphNodeService.removeAllConnections(phase)
        blueprintPhaseRepository.delete(phase)
        entityManager.flush()
        return DeleteBlueprintPhaseResponse(updatedPhases.map { it.toUpdateGraphNodeResponse() })
    }

//  ========================== Helper Methods ==========================

    private fun validateRevision(
        phase: BlueprintPhase,
        revision: Long,
    ) {
        if (phase.revision != revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint phase has been modified by another request. Please reload and try again.",
            )
        }
    }

    private fun shiftPhasesRight(
        blueprintPath: BlueprintPath,
        request: CreateBlueprintPhaseRequest,
    ) {
        val phaseCount = blueprintPhaseRepository.countByBlueprintPathId(blueprintPath.id)

        if (request.position !in 0..phaseCount) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Position must be between 0 and $phaseCount",
            )
        }

        val phasesToShift = blueprintPhaseRepository
            .findAllByBlueprintPathIdAndPositionGreaterThanEqualOrderByPositionDesc(
                blueprintPath.id,
                request.position,
            )

        phasesToShift.forEach { it.position += 1 }
    }

    private fun shiftPhasesBetween(
        blueprintPhase: BlueprintPhase,
        newPosition: Int,
    ): MutableList<BlueprintPhase> {
        val phaseCount = blueprintPhaseRepository.countByBlueprintPathId(blueprintPhase.blueprintPath.id)

        if (newPosition !in 0 until phaseCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${phaseCount - 1}")
        }

        val oldPosition = blueprintPhase.position
        var phasesToShift: MutableList<BlueprintPhase> = mutableListOf()

        if (oldPosition < newPosition) {
            phasesToShift = blueprintPhaseRepository
                .findAllByBlueprintPathIdAndPositionBetween(
                    blueprintPhase.blueprintPath.id,
                    oldPosition + 1,
                    newPosition,
                )

            phasesToShift.forEach { it.position -= 1 }
        }

        if (oldPosition > newPosition) {
            phasesToShift = blueprintPhaseRepository
                .findAllByBlueprintPathIdAndPositionBetween(
                    blueprintPhase.blueprintPath.id,
                    newPosition,
                    oldPosition - 1,
                )

            phasesToShift.forEach { it.position += 1 }
        }

        return phasesToShift
    }
}
