package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepOrigin
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSubGraphNode
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingStepRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Adds a step to a phase of the hire's own path *inside its dependency graph*, not only at the end
 * of its list.
 *
 * A phase is not a pipeline. Its steps and questions form a graph: an item opens once everything it
 * waits on is done, several can be open at once, and finishing one can open several. A step created
 * with no edges is an island -- open from the start, connected to nothing in the graph view, and
 * never "what comes next" however it was meant. So a step placed here says what it waits on and what
 * should now wait on it.
 *
 * ### Inserting between
 *
 * When the new step sits between an item A it waits on and an item B it unlocks, the direct edge
 * A → B is replaced by A → new → B. Keeping both would still be correct -- B waits on A through the
 * new step anyway -- but it draws a shortcut past the step that was just put in the way, and reads
 * as "B does not really need it".
 *
 * Only the hire's own copy is touched. The blueprint the path was copied from knows nothing of this.
 */
@Service
class OnboardingStepPlacementService(
    private val onboardingStepService: OnboardingStepService,
    private val onboardingStepRepository: OnboardingStepRepository,
) {
    /**
     * Creates the step, then connects it: it waits on [waitsOn], and every item in [unlocks] waits on
     * it. Both sets must be items of the same phase, and no item in [unlocks] may be something the
     * new step would itself (transitively) wait on -- that would be a cycle, and a cycle is a phase
     * nobody can ever finish.
     *
     * One transaction, so a step never exists half-connected.
     */
    @Transactional
    @Tracked("Creating a connected onboarding step for user")
    fun createConnectedStepForMe(
        authId: String,
        phaseId: UUID,
        request: CreateOnboardingStepRequest,
        origin: StepOrigin,
        waitsOn: Set<UUID>,
        unlocks: Set<UUID>,
    ): CreateOnboardingStepResponse {
        val created = onboardingStepService.createOnboardingStepForMe(authId, phaseId, request, origin)
        val step = onboardingStepRepository
            .findById(created.id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No step found with id: ${created.id}") }

        val phase = step.phase
        val nodes: Map<UUID, OnboardingSubGraphNode> =
            (phase.steps.filter { it.id != step.id } + phase.checkQuestions).associateBy { it.id }

        val before = waitsOn.map { nodes[it] ?: notInPhase(it) }
        val after = unlocks.map { nodes[it] ?: notInPhase(it) }

        if (before.any { it.id in unlocks } ||
            after.any { candidate -> before.any { waitsTransitivelyOn(it, candidate) } }
        ) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "The new step cannot both wait on and unlock the same item",
            )
        }

        step.blockedBy += before
        after.forEach { node ->
            node.blockedBy.removeIf { it.id in waitsOn }
            node.blockedBy += step
        }
        placeOnCanvas(step, before, after)

        return step.toCreateResponse()
    }

    /** Whether [node] waits on [candidate], directly or through anything it waits on. */
    private fun waitsTransitivelyOn(node: OnboardingSubGraphNode, candidate: OnboardingSubGraphNode): Boolean {
        val seen = mutableSetOf<UUID>()
        val stack = ArrayDeque(listOf(node))
        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            if (current.id == candidate.id) return true
            if (seen.add(current.id)) stack.addAll(current.blockedBy)
        }
        return false
    }

    /**
     * Puts the step where the graph view will draw it between its neighbours.
     *
     * Graphs here flow top to bottom, so the step goes below what it waits on and above what it
     * unlocks, centred on them. Nothing to centre on leaves the coordinates empty, and the viewer
     * lays it out itself.
     */
    private fun placeOnCanvas(
        step: OnboardingStep,
        before: List<OnboardingSubGraphNode>,
        after: List<OnboardingSubGraphNode>,
    ) {
        val above = before.mapNotNull { node -> node.graphY?.let { y -> node.graphX?.let { x -> x to y } } }
        val below = after.mapNotNull { node -> node.graphY?.let { y -> node.graphX?.let { x -> x to y } } }
        val neighbours = above + below
        if (neighbours.isEmpty()) return

        val y = when {
            above.isNotEmpty() && below.isNotEmpty() -> (above.maxOf { it.second } + below.minOf { it.second }) / 2
            above.isNotEmpty() -> above.maxOf { it.second } + ROW_GAP
            else -> below.minOf { it.second } - ROW_GAP
        }
        step.graphX = neighbours.map { it.first }.average()
        step.graphY = y
    }

    private fun notInPhase(id: UUID): Nothing =
        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Item $id is not part of this phase")

    private companion object {
        /** The vertical distance between one row of a phase graph and the next. */
        const val ROW_GAP = 180.0
    }
}
