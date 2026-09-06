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

@Service
class BlueprintGraphNodeService(
    private val blueprintPhaseRepository: BlueprintPhaseRepository,
    private val blueprintAccessService: BlueprintAccessService,
    private val entityManager: EntityManager,
) {
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
