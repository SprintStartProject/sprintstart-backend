package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintSubGraphNode
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toAddBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetSubGraphNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toRemoveBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toRemovePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.AddBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.UpdateBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.AddBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.GetBlueprintSubGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.UpdateBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintGraphNodeRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class BlueprintSubGraphNodeService(
    private val blueprintAccessService: BlueprintAccessService,
    private val entityManager: EntityManager,
    private val blueprintGraphNodeRepository: BlueprintGraphNodeRepository,
) {
    @Transactional
    fun getSubGraph(scope: BlueprintScope, phaseId: UUID): GetBlueprintSubGraphResponse {
        return GetBlueprintSubGraphResponse(
            when (scope) {
                is BlueprintScope.Global -> {
                    blueprintGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintPhaseId(
                        phaseId,
                    )
                }

                is BlueprintScope.Project -> {
                    blueprintGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(
                        scope.projectId,
                        phaseId,
                    )
                }
            }.map { it.toGetSubGraphNodeResponse() },
        )
    }

    @Transactional
    fun addSubGraphNodeBlocker(
        scope: BlueprintScope,
        nodeId: UUID,
        blockerId: UUID,
        request: AddBlueprintSubGraphNodeBlockerRequest,
    ): AddBlueprintSubGraphNodeBlockerResponse {
        val node = blueprintAccessService.getAuthorizedEditableSubGraphNode(scope, nodeId)
        val blocker = blueprintAccessService.getAuthorizedEditableSubGraphNode(scope, blockerId)
        validateRevision(node, request.revision)

        if (node.blockedBy.map { it.id }.contains(blockerId)) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Cant be blocked by an existing blocker")
        }

        dfs(blocker, node)
        markNodeModified(node)
        node.blockedBy.add(blocker)
        entityManager.flush()

        return node.toAddBlockerResponse()
    }

    @Transactional
    fun updateSubGraphNodePositionById(
        scope: BlueprintScope,
        nodeId: UUID,
        request: UpdateBlueprintSubGraphNodePositionRequest,
    ): UpdateBlueprintSubGraphNodePositionResponse {
        val node = blueprintAccessService.getAuthorizedEditableSubGraphNode(scope, nodeId)
        validateRevision(node, request.revision)

        node.graphX = request.graphX
        node.graphY = request.graphY
        entityManager.flush()

        return node.toUpdatePositionResponse()
    }

    @Transactional
    fun removeSubGraphNodeBlocker(
        scope: BlueprintScope,
        nodeId: UUID,
        blockerId: UUID,
        request: RemoveBlueprintSubGraphNodeBlockerRequest,
    ): RemoveBlueprintSubGraphNodeBlockerResponse {
        val node = blueprintAccessService.getAuthorizedEditableSubGraphNode(scope, nodeId)
        val blocker = blueprintAccessService.getAuthorizedEditableSubGraphNode(scope, blockerId)

        validateRevision(node, request.revision)

        markNodeModified(node)
        node.blockedBy.remove(blocker)
        entityManager.flush()

        return node.toRemoveBlockerResponse()
    }

    @Transactional
    fun removeSubGraphNodePositionById(
        scope: BlueprintScope,
        nodeId: UUID,
        request: RemoveBlueprintSubGraphNodePositionRequest,
    ): RemoveBlueprintSubGraphNodePositionResponse {
        val node = blueprintAccessService.getAuthorizedEditableSubGraphNode(scope, nodeId)
        validateRevision(node, request.revision)

        node.graphX = null
        node.graphY = null
        node.blockedBy.clear()
        entityManager.flush()

        return node.toRemovePositionResponse()
    }

// ========== Helper methods ===========

    private fun dfs(currentNode: BlueprintSubGraphNode, targetNode: BlueprintSubGraphNode) {
        if (currentNode.id == targetNode.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Blocking cant be cyclic")
        }

        for (node in currentNode.blockedBy) {
            dfs(node, targetNode)
        }
    }

    private fun markNodeModified(node: BlueprintSubGraphNode) {
        entityManager.lock(
            node,
            LockModeType.OPTIMISTIC_FORCE_INCREMENT,
        )
    }

    private fun validateRevision(
        node: BlueprintSubGraphNode,
        revision: Long,
    ) {
        if (node.revision != revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint node has been modified by another request. Please reload and try again.",
            )
        }
    }
}
