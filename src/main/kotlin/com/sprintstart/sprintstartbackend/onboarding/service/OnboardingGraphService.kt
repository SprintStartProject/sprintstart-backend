package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSubGraphNode
import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.ArrangeOnboardingGraphRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.ReplaceOnboardingBlockersRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.graph.OnboardingBlockersResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPhaseRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingSubGraphNodeRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.UserApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Edits the two graphs of somebody's onboarding path: the phases of the path, and the steps and
 * questions inside each phase.
 *
 * Two kinds of change, with different owners:
 *
 * - **Layout** -- where a node sits on the canvas. It changes nothing about what is open or locked,
 *   so the hire may arrange their own graph as well as a PM may arrange it for them.
 * - **Edges** -- what waits on what. An edge decides when an item unlocks, so only a PM, HR or an
 *   admin changes them, and never into a cycle: a cycle is a set of items that all wait on each other,
 *   which nobody can ever finish.
 *
 * Only the path's own copy is touched. The blueprint it was copied from knows nothing of this.
 */
@Service
class OnboardingGraphService(
    private val userApi: UserApi,
    private val onboardingPathRepository: OnboardingPathRepository,
    private val onboardingPhaseRepository: OnboardingPhaseRepository,
    private val onboardingSubGraphNodeRepository: OnboardingSubGraphNodeRepository,
) {
    /**
     * Moves steps and questions of one phase of the caller's own path.
     *
     * @throws ResponseStatusException 404 when the phase is not on the caller's path, 400 when a
     * position names something that is not a node of the phase or is not a finite number.
     */
    @Transactional
    @Tracked("Arranging an onboarding phase graph for user")
    fun arrangePhaseForMe(authId: String, phaseId: UUID, request: ArrangeOnboardingGraphRequest) {
        val userId = userIdOf(authId)
        val phase = onboardingPhaseRepository
            .findByIdAndPathUserId(phaseId, userId)
            .orElseThrow { notFound("No phase found with id: $phaseId") }
        arrangePhase(phase, request)
    }

    /** Moves steps and questions of any phase. PM, HR and admins only (enforced by the controller). */
    @Transactional
    @Tracked("Arranging an onboarding phase graph")
    fun arrangePhaseById(phaseId: UUID, request: ArrangeOnboardingGraphRequest) {
        arrangePhase(findPhase(phaseId), request)
    }

    /** Moves the phases of the caller's own path. */
    @Transactional
    @Tracked("Arranging an onboarding path graph for user")
    fun arrangePathForMe(authId: String, request: ArrangeOnboardingGraphRequest) {
        arrangePath(findPathOfUser(userIdOf(authId)), request)
    }

    /** Moves the phases of a user's path. PM, HR and admins only (enforced by the controller). */
    @Transactional
    @Tracked("Arranging an onboarding path graph")
    fun arrangePathForUser(userId: UUID, request: ArrangeOnboardingGraphRequest) {
        arrangePath(findPathOfUser(userId), request)
    }

    /**
     * Replaces everything a step or question waits on.
     *
     * @throws ResponseStatusException 404 when the node does not exist; 400 when a blocker is not in
     * the same phase, is the node itself, or would close a cycle.
     */
    @Transactional
    @Tracked("Replacing the blockers of an onboarding graph node")
    fun replaceNodeBlockers(nodeId: UUID, request: ReplaceOnboardingBlockersRequest): OnboardingBlockersResponse {
        val node = onboardingSubGraphNodeRepository
            .findById(nodeId)
            .orElseThrow { notFound("No step or question found with id: $nodeId") }
        val siblings: Map<UUID, OnboardingSubGraphNode> =
            (node.phase.steps + node.phase.checkQuestions).associateBy { it.id }

        blockerProblem(node.id, request.blockerIds, siblings.keys) { id ->
            siblings[id]?.blockedBy.orEmpty().map { it.id }
        }?.let { throw badRequest(it) }

        node.blockedBy.clear()
        node.blockedBy += request.blockerIds.map { siblings.getValue(it) }
        return OnboardingBlockersResponse(id = node.id, blockerIds = node.blockedBy.map { it.id }.toSet())
    }

    /**
     * Replaces the phases a phase waits on.
     *
     * @throws ResponseStatusException 404 when the phase does not exist; 400 when a blocker is on
     * another path, is the phase itself, or would close a cycle.
     */
    @Transactional
    @Tracked("Replacing the blockers of an onboarding phase")
    fun replacePhaseBlockers(phaseId: UUID, request: ReplaceOnboardingBlockersRequest): OnboardingBlockersResponse {
        val phase = findPhase(phaseId)
        val siblings = onboardingPhaseRepository.findAllByPathId(phase.path.id).associateBy { it.id }

        blockerProblem(phase.id, request.blockerIds, siblings.keys) { id ->
            siblings[id]?.blockedBy.orEmpty().map { it.id }
        }?.let { throw badRequest(it) }

        phase.blockedBy.clear()
        phase.blockedBy += request.blockerIds.map { siblings.getValue(it) }
        return OnboardingBlockersResponse(id = phase.id, blockerIds = phase.blockedBy.map { it.id }.toSet())
    }

    private fun arrangePhase(phase: OnboardingPhase, request: ArrangeOnboardingGraphRequest) {
        val nodes: Map<UUID, OnboardingSubGraphNode> = (phase.steps + phase.checkQuestions).associateBy { it.id }
        request.nodes.forEach { position ->
            val node = nodes[position.id] ?: throw badRequest("Item ${position.id} is not part of this phase")
            requireFinite(position.graphX, position.graphY)
            node.graphX = position.graphX
            node.graphY = position.graphY
        }
    }

    private fun arrangePath(path: OnboardingPath, request: ArrangeOnboardingGraphRequest) {
        val phases = onboardingPhaseRepository.findAllByPathId(path.id).associateBy { it.id }
        request.nodes.forEach { position ->
            val phase = phases[position.id] ?: throw badRequest("Phase ${position.id} is not part of this path")
            requireFinite(position.graphX, position.graphY)
            phase.graphX = position.graphX
            phase.graphY = position.graphY
        }
    }

    private fun userIdOf(authId: String): UUID =
        userApi.getUserIdByAuthId(authId).orElseThrow { notFound("User not found") }

    private fun findPathOfUser(userId: UUID): OnboardingPath =
        onboardingPathRepository
            .findOnboardingPathByUserId(userId)
            .orElseThrow { notFound("Onboarding path not found") }

    private fun findPhase(phaseId: UUID): OnboardingPhase =
        onboardingPhaseRepository.findById(phaseId).orElseThrow { notFound("No phase found with id: $phaseId") }
}

/**
 * Why [blockerIds] cannot be what [nodeId] waits on, as a sentence, or null when they can: every
 * blocker must be one of [siblings], none may be the node itself, and none may already (transitively)
 * wait on the node -- that edge would close a loop.
 */
private fun blockerProblem(
    nodeId: UUID,
    blockerIds: Set<UUID>,
    siblings: Set<UUID>,
    blockersOf: (UUID) -> List<UUID>,
): String? =
    when {
        blockerIds.any { it !in siblings } -> "Every blocker must belong to the same graph"
        nodeId in blockerIds -> "An item cannot wait on itself"
        blockerIds.any { waitsTransitivelyOn(it, nodeId, blockersOf) } ->
            "That connection would make a loop: the item already unlocks one of its new blockers"
        else -> null
    }

/** Whether [start] waits on [target], directly or through anything it waits on. */
private fun waitsTransitivelyOn(start: UUID, target: UUID, blockersOf: (UUID) -> List<UUID>): Boolean {
    val seen = mutableSetOf<UUID>()
    val stack = ArrayDeque(listOf(start))
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        if (current == target) return true
        if (seen.add(current)) stack.addAll(blockersOf(current))
    }
    return false
}

private fun requireFinite(x: Double, y: Double) {
    if (!x.isFinite() || !y.isFinite()) throw badRequest("Graph positions must be finite numbers")
}

private fun notFound(message: String) = ResponseStatusException(HttpStatus.NOT_FOUND, message)

private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)
