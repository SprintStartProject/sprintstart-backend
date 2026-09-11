package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toAddBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetGraphNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toRemoveBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateGraphNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateGraphPositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.AddBlueprintGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.RemoveBlueprintGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.RemoveBlueprintGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.graphNode.UpdateBlueprintGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.AddBlueprintGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.GetBlueprintGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.RemoveBlueprintGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.RemoveBlueprintGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.graphNode.UpdateBlueprintGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPhaseRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Manages phase coordinates and blocker relationships in a blueprint graph.
 *
 * Both endpoints of an edge are authorized in the same scope. Relationship mutations use forced optimistic version
 * increments, duplicate and cyclic blockers are rejected, and removing a phase from the layout disconnects all of its
 * incoming and outgoing edges.
 */
@Service
class BlueprintGraphNodeService(
    private val blueprintPhaseRepository: BlueprintPhaseRepository,
    private val blueprintAccessService: BlueprintAccessService,
    private val entityManager: EntityManager,
) {
    /**
     * Returns graph.
     *
     * Loads phases with the repository query matching the requested scope and maps their coordinates and blocker edges.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param pathId Identifier of the blueprint path.
     * @return The mapped result of the operation.
     */
    @Transactional
    fun getGraph(scope: BlueprintScope, pathId: UUID): GetBlueprintGraphResponse {
        return GetBlueprintGraphResponse(
            when (scope) {
                is BlueprintScope.Global -> {
                    blueprintPhaseRepository.findAllByBlueprintPathProjectIdIsNullAndBlueprintPathId(
                        pathId,
                    )
                }

                is BlueprintScope.Project -> {
                    blueprintPhaseRepository.findAllByBlueprintPathProjectIdAndBlueprintPathId(
                        scope.projectId,
                        pathId,
                    )
                }
            }.map { it.toGetGraphNodeResponse() },
        )
    }

    /**
     * Adds graph node blocker.
     *
     * Validates the target revision, rejects duplicate and cyclic edges, then forces an optimistic version increment.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @param blockerId Operation input.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 400 for a cycle, 403 for a duplicate edge, 404 for a missing phase, or 409
     *   for stale/non-editable state.
     */
    @Transactional
    fun addGraphNodeBlocker(
        scope: BlueprintScope,
        phaseId: UUID,
        blockerId: UUID,
        request: AddBlueprintGraphNodeBlockerRequest,
    ): AddBlueprintGraphNodeBlockerResponse {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)
        val blocker = blueprintAccessService.getAuthorizedEditablePhase(scope, blockerId)
        validateRevision(phase, request.revision)

        if (phase.blockedBy.map { it.id }.contains(blockerId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cant be blocked by an existing blocker")
        }

        dfs(blocker, phase)
        markPhaseModified(phase)
        phase.blockedBy.add(blocker)
        entityManager.flush()

        return phase.toAddBlockerResponse()
    }

    /**
     * Updates graph node position by id.
     *
     * Validates the target revision, replaces both coordinates, and flushes before mapping the response.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 for a missing phase, or 409 for stale/non-editable state.
     */
    @Transactional
    fun updateGraphNodePositionById(
        scope: BlueprintScope,
        phaseId: UUID,
        request: UpdateBlueprintGraphNodePositionRequest,
    ): UpdateBlueprintGraphNodePositionResponse {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)
        validateRevision(phase, request.revision)

        phase.graphX = request.graphX
        phase.graphY = request.graphY
        entityManager.flush()

        return phase.toUpdateGraphPositionResponse()
    }

    /**
     * Removes graph node blocker.
     *
     * Validates the target revision, removes the selected edge, and forces an optimistic version increment.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @param blockerId Operation input.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 for a missing phase, or 409 for stale/non-editable state.
     */
    @Transactional
    fun removeGraphNodeBlocker(
        scope: BlueprintScope,
        phaseId: UUID,
        blockerId: UUID,
        request: RemoveBlueprintGraphNodeBlockerRequest,
    ): RemoveBlueprintGraphNodeBlockerResponse {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)
        val blocker = blueprintAccessService.getAuthorizedEditablePhase(scope, blockerId)

        validateRevision(phase, request.revision)

        markPhaseModified(phase)
        phase.blockedBy.remove(blocker)
        entityManager.flush()

        return phase.toRemoveBlockerResponse()
    }

    /**
     * Removes graph node position by id.
     *
     * Clears coordinates and removes all incoming and outgoing blocker edges, returning every affected revision.
     *
     * @param scope Ownership boundary used for repository selection and authorization.
     * @param phaseId Identifier of the blueprint phase.
     * @param request Request data, including the expected revision when optimistic concurrency applies.
     * @return The mapped result of the operation.
     * @throws ResponseStatusException With 404 for a missing phase, or 409 for stale/non-editable state.
     */
    @Transactional
    fun removeGraphNodePositionById(
        scope: BlueprintScope,
        phaseId: UUID,
        request: RemoveBlueprintGraphNodePositionRequest,
    ): RemoveBlueprintGraphNodePositionResponse {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)
        validateRevision(phase, request.revision)

        phase.graphX = null
        phase.graphY = null
        markPhaseModified(phase)
        val changedNodes = removeAllConnections(phase)
        entityManager.flush()

        return RemoveBlueprintGraphNodePositionResponse(
            changedNodes.map { it.toUpdateGraphNodeResponse() },
        )
    }

//  ================ Helper methods ==================

    private fun dfs(currentNode: BlueprintPhase, targetNode: BlueprintPhase) {
        if (currentNode.id == targetNode.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Blocking cant be cyclic")
        }

        for (node in currentNode.blockedBy) {
            dfs(node, targetNode)
        }
    }

    /**
     * Removes all connections.
     *
     * Removes incoming edges from dependants and clears outgoing edges. The caller owns the transaction and
     * target-node version handling.
     *
     * @param phase Phase whose graph relationships should be removed.
     * @return The mapped result of the operation.
     */

    fun removeAllConnections(phase: BlueprintPhase): List<BlueprintPhase> {
        val dependants = blueprintPhaseRepository.findAllBlockedById(phase.id)
        val changedNodes = listOf(phase) + dependants

        dependants.forEach {
            markPhaseModified(it)
            it.blockedBy.remove(phase)
        }

        phase.blockedBy.clear()

        return changedNodes
    }

    private fun markPhaseModified(phase: BlueprintPhase) {
        entityManager.lock(
            phase,
            LockModeType.OPTIMISTIC_FORCE_INCREMENT,
        )
    }

    private fun validateRevision(
        phase: BlueprintPhase,
        revision: Long,
    ) {
        if (phase.revision != revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint node has been modified by another request. Please reload and try again.",
            )
        }
    }
}
