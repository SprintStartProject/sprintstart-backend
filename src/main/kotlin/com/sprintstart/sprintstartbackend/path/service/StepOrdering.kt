package com.sprintstart.sprintstartbackend.path.service

import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.path.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.path.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.path.repository.OnboardingStepRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/** Keeps a phase's steps in a contiguous order and its dependency graph connected as steps come and go. */
internal class StepOrdering(
    private val onboardingStepRepository: OnboardingStepRepository,
) {
    /** Deletes a step: the siblings behind it move up, the graph closes over it, then it goes. */
    fun remove(step: OnboardingStep) {
        onboardingStepRepository
            .findAllByPhaseIdAndPositionGreaterThan(step.phase.id, step.position)
            .forEach { it.position -= 1 }
        bridgeOverInGraph(step)
        onboardingStepRepository.delete(step)
    }

    /**
     * Takes a step out of its phase's dependency graph before it is deleted, joining up what it sat
     * between.
     *
     * Blocker edges are a many-to-many between subgraph nodes, and deleting a step removes only the
     * rows it owns -- the ones saying what *it* waits on. The rows saying what waits on *it* belong to
     * the other nodes and outlived it: the delete failed on the join table, or the items after it
     * stayed locked behind a step nobody could finish any more. PMs add and delete steps right in the
     * graph, and a hire can delete the ones they add with their buddy, so this is not a corner case.
     *
     * Bridged rather than only cut: for A -> X -> B, deleting X leaves A -> B, so B still opens after
     * what it opened after before X was put in the way -- instead of suddenly opening at once.
     */
    fun bridgeOverInGraph(step: OnboardingStep) {
        val phase = step.phase
        val dependents = (phase.steps + phase.checkQuestions)
            .filter { node -> node.id != step.id && node.blockedBy.any { it.id == step.id } }
        dependents.forEach { node ->
            node.blockedBy.removeIf { it.id == step.id }
            node.blockedBy += step.blockedBy.filter { it.id != node.id }
        }
        step.blockedBy.clear()
        // Loading the siblings above put the step's phase collection in play, and that collection
        // cascades: while it still holds the step, Hibernate quietly un-schedules the delete at flush,
        // and the request "succeeds" with the step still there. Out of the collection, orphan removal
        // and the delete agree.
        phase.steps.removeIf { it.id == step.id }
    }

    /**
     * Makes room for a new step at the requested position by shifting all existing
     * steps at that position or after it one position to the right.
     *
     * Valid positions are from `0` to `stepCount`, where `stepCount` means
     * appending the new step at the end.
     *
     * @throws ResponseStatusException with `400 BAD_REQUEST` if the requested
     * position is outside the valid range.
     */
    fun shiftStepsRight(
        phase: OnboardingPhase,
        request: CreateOnboardingStepRequest,
    ) {
        val stepCount = onboardingStepRepository.countByPhaseId(phase.id)

        if (request.position !in 0..stepCount) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Position must be between 0 and $stepCount",
            )
        }

        val stepsToShift = onboardingStepRepository
            .findByPhaseIdAndPositionGreaterThanEqualOrderByPositionDesc(
                phase.id,
                request.position,
            )

        stepsToShift.forEach { it.position += 1 }
    }

    /**
     * Reorders steps after an existing step is moved to a new position.
     *
     * When the step moves down, all steps between the old and new position are
     * shifted one position to the left. When the step moves up, all steps between
     * the new and old position are shifted one position to the right.
     *
     * Valid positions are from `0` to `stepCount - 1`, because the step already
     * exists and can only be moved within the current list.
     *
     * @throws ResponseStatusException with `400 BAD_REQUEST` if the requested
     * position is outside the valid range.
     */
    fun shiftStepsBetween(
        step: OnboardingStep,
        request: UpdateOnboardingStepRequest,
    ) {
        val stepCount = onboardingStepRepository.countByPhaseId(step.phase.id)

        if (request.position !in 0 until stepCount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Position must be between 0 and ${stepCount - 1}")
        }

        val oldPosition = step.position
        val newPosition = request.position

        if (oldPosition < newPosition) {
            val stepsToShift = onboardingStepRepository
                .findByPhaseIdAndPositionBetween(
                    step.phase.id,
                    oldPosition + 1,
                    newPosition,
                )

            stepsToShift.forEach { it.position -= 1 }
        }

        if (oldPosition > newPosition) {
            val stepsToShift = onboardingStepRepository
                .findByPhaseIdAndPositionBetween(
                    step.phase.id,
                    newPosition,
                    oldPosition - 1,
                )

            stepsToShift.forEach { it.position += 1 }
        }
    }
}
