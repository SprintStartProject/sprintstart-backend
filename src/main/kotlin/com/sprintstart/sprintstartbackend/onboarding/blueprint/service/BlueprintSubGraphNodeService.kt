package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintSubGraphNode
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toAddBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toGetSubGraphNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toRemoveBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdatePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toUpdateSubGraphNodeResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.AddBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodeBlockerRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.RemoveBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.subGraphNode.UpdateBlueprintSubGraphNodePositionRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.AddBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.GetBlueprintSubGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodeBlockerResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.RemoveBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.subGraphNode.UpdateBlueprintSubGraphNodePositionResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintSubGraphNodeRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Manages graph-specific state shared by blueprint steps and check questions.
 *
 * The service resolves editable nodes through [BlueprintAccessService], applies optimistic revision checks before
 * mutations, and forces version increments when blocker relationships change. Blocker edges are kept acyclic, and
 * removing a node's graph position also disconnects every incoming and outgoing edge so unplaced nodes cannot remain
 * part of the rendered graph.
 */
@Service
class BlueprintSubGraphNodeService(
    private val blueprintAccessService: BlueprintAccessService,
    private val entityManager: EntityManager,
    private val blueprintSubGraphNodeRepository: BlueprintSubGraphNodeRepository,
) {
    /**
     * Returns every sub-graph node in the requested blueprint phase and scope.
     *
     * Global scope selects nodes belonging to blueprint paths without a project, while project scope restricts the
     * result to the supplied project. The response includes both steps and check questions in repository order.
     *
     * @param scope blueprint ownership boundary used to select nodes
     * @param phaseId phase whose sub-graph should be returned
     * @return the mapped nodes belonging to the phase in the requested scope
     */
    @Transactional
    fun getSubGraph(scope: BlueprintScope, phaseId: UUID): GetBlueprintSubGraphResponse {
        return GetBlueprintSubGraphResponse(
            when (scope) {
                is BlueprintScope.Global -> {
                    blueprintSubGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdIsNullAndBlueprintPhaseId(
                        phaseId,
                    )
                }

                is BlueprintScope.Project -> {
                    blueprintSubGraphNodeRepository.findByBlueprintPhaseBlueprintPathProjectIdAndBlueprintPhaseId(
                        scope.projectId,
                        phaseId,
                    )
                }
            }.map { it.toGetSubGraphNodeResponse() },
        )
    }

    /**
     * Adds a directed blocker relationship to an editable sub-graph node.
     *
     * Both nodes must be editable in the same [scope]. The supplied revision must match the blocked node, an existing
     * edge cannot be added twice, and the new edge must not create a direct or transitive cycle. A successful change
     * forces an optimistic version increment for the blocked node.
     *
     * @param scope blueprint ownership boundary used to authorize both nodes
     * @param nodeId identifier of the node that will be blocked
     * @param blockerId identifier of the node that will become a blocker
     * @param request expected revision of the blocked node
     * @return the predicted next revision and complete blocker ID set
     * @throws ResponseStatusException with 409 when the revision is stale, 403 when the edge already exists, or 400
     * when the edge would create a cycle
     */
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

    /**
     * Updates the graph coordinates of an editable sub-graph node.
     *
     * @param scope blueprint ownership boundary used to authorize the node
     * @param nodeId identifier of the node to move
     * @param request expected revision and replacement coordinates
     * @return the node's current revision and updated coordinates
     * @throws ResponseStatusException with 409 when the revision is stale
     */
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

    /**
     * Removes a blocker relationship from an editable sub-graph node.
     *
     * Both nodes are resolved in the same [scope]. A successful mutation forces an optimistic version increment for
     * the blocked node and returns its remaining blocker IDs.
     *
     * @param scope blueprint ownership boundary used to authorize both nodes
     * @param nodeId identifier of the blocked node
     * @param blockerId identifier of the blocker to remove
     * @param request expected revision of the blocked node
     * @return the predicted next revision and remaining blocker ID set
     * @throws ResponseStatusException with 409 when the revision is stale
     */
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

    /**
     * Clears an editable node's graph coordinates and disconnects all of its blocker relationships.
     *
     * Incoming edges from dependant nodes and outgoing edges from the target are removed together. Every affected
     * node is marked for an optimistic version increment and represented in the response.
     *
     * @param scope blueprint ownership boundary used to authorize the node
     * @param nodeId identifier of the node to remove from the graph layout
     * @param request expected revision of the node
     * @return identifiers and predicted next revisions for every affected node
     * @throws ResponseStatusException with 409 when the revision is stale
     */
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
        markNodeModified(node)
        val changedNodes = removeAllConnections(node)
        entityManager.flush()

        return RemoveBlueprintSubGraphNodePositionResponse(
            changedNodes.map { it.toUpdateSubGraphNodeResponse() },
        )
    }

    private fun dfs(currentNode: BlueprintSubGraphNode, targetNode: BlueprintSubGraphNode) {
        if (currentNode.id == targetNode.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Blocking cant be cyclic")
        }

        for (node in currentNode.blockedBy) {
            dfs(node, targetNode)
        }
    }

    /**
     * Removes every incoming and outgoing blocker relationship for [node].
     *
     * The caller must execute this operation in a transaction and manage any required version change for [node]. Each
     * dependant whose incoming edge is removed is marked for an optimistic version increment. The returned list always
     * starts with [node], followed by dependants in repository order.
     *
     * @param node node whose blocker relationships should be cleared
     * @return every node whose persisted relationship state was changed
     */
    fun removeAllConnections(node: BlueprintSubGraphNode): List<BlueprintSubGraphNode> {
        val dependants = blueprintSubGraphNodeRepository.findAllByBlockedBy(node.id)
        val changedNodes = listOf(node) + dependants

        dependants.forEach {
            markNodeModified(it)
            it.blockedBy.remove(node)
        }

        node.blockedBy.clear()

        return changedNodes
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
