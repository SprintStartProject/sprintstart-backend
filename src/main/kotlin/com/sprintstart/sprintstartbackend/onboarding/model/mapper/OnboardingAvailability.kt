package com.sprintstart.sprintstartbackend.onboarding.model.mapper

import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingSubGraphNode
import com.sprintstart.sprintstartbackend.onboarding.model.entity.PhaseCheckQuestion
import java.util.UUID

/** Whether this phase is complete from the user's perspective: all steps done, all questions passed. */
fun OnboardingPhase.isCompleteFor(passedQuestionIds: Set<UUID>): Boolean {
    return steps.all { it.status == StepStatus.FINISHED || it.status == StepStatus.SKIPPED } &&
        checkQuestions.all { it.id in passedQuestionIds }
}

/**
 * Locked state for every phase of a path, keyed by phase id.
 *
 * The blocker graph is traversed transitively; a phase whose blocker is itself locked is
 * locked too. Blockers that were filtered out of the path (role/skill exclusions) carry no
 * state and are ignored.
 */
fun computePhaseLockedState(
    phases: List<OnboardingPhase>,
    passedQuestionIds: Set<UUID>,
): Map<UUID, Boolean> {
    val phaseById = phases.associateBy { it.id }
    val result = mutableMapOf<UUID, Boolean>()
    val visiting = mutableSetOf<UUID>()

    fun isLocked(phase: OnboardingPhase): Boolean {
        result[phase.id]?.let { return it }
        if (!visiting.add(phase.id)) return true
        val locked = phase.blockedBy.any { blocker ->
            val blockerPhase = phaseById[blocker.id]
            blockerPhase != null && (!blockerPhase.isCompleteFor(passedQuestionIds) || isLocked(blockerPhase))
        }
        visiting.remove(phase.id)
        result[phase.id] = locked
        return locked
    }

    phases.forEach { isLocked(it) }
    return result
}

/** Whether this subgraph node counts as complete from the user's perspective. */
fun OnboardingSubGraphNode.isComplete(passedQuestionIds: Set<UUID>): Boolean = when (this) {
    is OnboardingStep -> status == StepStatus.FINISHED || status == StepStatus.SKIPPED
    is PhaseCheckQuestion -> id in passedQuestionIds
    else -> true
}

/**
 * Whether a subgraph node is locked: its phase is locked, or any of its direct in-phase
 * blockers is not yet complete.
 */
fun OnboardingSubGraphNode.isLockedIn(
    phaseLocked: Boolean,
    passedQuestionIds: Set<UUID>,
): Boolean {
    if (phaseLocked) return true
    return blockedBy.any { blocker -> !blocker.isComplete(passedQuestionIds) }
}

/** The user-facing status of a question, derived from its lock state and attempt history. */
fun OnboardingSubGraphNode.questionStatus(
    phaseLocked: Boolean,
    passedQuestionIds: Set<UUID>,
    attemptedQuestionIds: Set<UUID>,
): QuestionStatus {
    if (isLockedIn(phaseLocked, passedQuestionIds)) return QuestionStatus.LOCKED
    if (id in passedQuestionIds) return QuestionStatus.PASSED
    return if (id in attemptedQuestionIds) QuestionStatus.RETRY else QuestionStatus.OPEN
}
