package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Where somebody is in their onboarding path: the step they are on, and how much is behind them.
 *
 * Extracted for the same reason [CurrentTaskReader] and [OpenPullRequestReader] were: "which step is
 * this person on" must not be a question two callers can answer differently. The rule used to live
 * only inside the team overview, private to one service, so the escalation inbox could either
 * duplicate it or go without — and a PM told one thing in the inbox and another on the team page has
 * no way to know which is true.
 *
 * Read-only. Seeing where somebody is must never be what moves them along.
 */
@Component
class OnboardingPositionReader(
    private val onboardingPathRepository: OnboardingPathRepository,
) {
    /**
     * The step [path] is resting on, with the phase it belongs to, or `null` when there is none.
     *
     * Phases in `position` order, then the first step in `position` order that is [StepStatus.WAITING]
     * or [StepStatus.IN_PROGRESS]. A path whose steps are all finished or skipped has no active step,
     * which is the honest answer rather than the last step somebody touched.
     *
     * Sorted here rather than trusted from the `@OrderBy` on the collections: this is the definition
     * of the rule, and a rule that only holds while its caller loaded the entity a particular way is
     * one waiting to be broken by a caller that did not.
     */
    fun activeStepIn(path: OnboardingPath): ActiveStep? {
        for (phase in path.phases.sortedBy { it.position }) {
            val step = phase.steps
                .sortedBy { it.position }
                .firstOrNull { it.status == StepStatus.WAITING || it.status == StepStatus.IN_PROGRESS }
            if (step != null) return ActiveStep(phase = phase, step = step)
        }
        return null
    }

    /**
     * How far through [path] somebody is, from 0.0 to 1.0.
     *
     * Skipped counts as behind them: a step they were excused is not work still waiting, and counting
     * it as outstanding would leave a hire who legitimately skipped two steps permanently short of
     * complete. A path with no steps is 0.0, never a division by zero.
     */
    fun progressOf(path: OnboardingPath): Double {
        val total = path.phases.sumOf { it.steps.size }
        if (total == 0) return 0.0

        val done = path.phases.sumOf { phase ->
            phase.steps.count { it.status == StepStatus.FINISHED || it.status == StepStatus.SKIPPED }
        }
        return done.toDouble() / total.toDouble()
    }

    /**
     * Where each of [userIds] stands, keyed by user.
     *
     * One repository call to find the paths, rather than one per user: callers reach for this while
     * decorating a list, and a per-row read is how a queue of ten questions becomes ten round trips.
     *
     * **Not free beyond that.** `phases` and `steps` are lazy, so reading them costs a query per
     * path and per phase — the same shape the team overview has always had. Fine for a page somebody
     * opened; not fine for anything on a hot path, which is why the sidebar badge counts rows
     * instead of calling through here.
     *
     * A user with no path is simply absent from the map. "Has not started" is a fact the caller
     * should get to phrase, not one this reader should invent a position for.
     *
     * Must be called inside a transaction: the phases and steps are read lazily.
     */
    fun positionsFor(userIds: Collection<UUID>): Map<UUID, OnboardingPosition> {
        val distinct = userIds.distinct()
        if (distinct.isEmpty()) return emptyMap()

        return onboardingPathRepository
            .findByUserIdIn(distinct)
            .associate { path ->
                val active = activeStepIn(path)
                path.userId to OnboardingPosition(
                    currentPhase = active?.phase?.title,
                    currentStep = active?.step?.title,
                    progressPercentage = progressOf(path),
                )
            }
    }
}

/** The step somebody is resting on, with the phase that holds it. */
data class ActiveStep(
    val phase: OnboardingPhase,
    val step: OnboardingStep,
)

/**
 * Where somebody stands in their onboarding, in the words a reader can put on a screen.
 *
 * Titles rather than entities: every consumer so far wants to *say* where somebody is, and handing
 * out the entities would let a caller reach through this into the path and start writing to it.
 */
data class OnboardingPosition(
    val currentPhase: String?,
    val currentStep: String?,
    val progressPercentage: Double,
)
