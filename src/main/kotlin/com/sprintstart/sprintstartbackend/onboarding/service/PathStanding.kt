package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import java.util.UUID

/**
 * Where the hire stands on a path whose phases run side by side, and what comes next in a phase.
 *
 * Both are the hire's own page's rules (`resolveNextAction` and `nextItemInPhase` in the frontend),
 * restated here so the mentor and the page cannot disagree about them. Split out of [BuddyPathTools],
 * which turns the answer into text; this only decides it.
 */
internal sealed interface PathStanding {
    /** Working in one phase -- the one to describe in full. */
    data class In(
        val phase: GetOnboardingPhaseForUserResponse,
    ) : PathStanding

    /** Several phases open and none of them started: the hire picks. */
    data class Choosing(
        val phases: List<GetOnboardingPhaseForUserResponse>,
    ) : PathStanding

    data object Finished : PathStanding

    companion object {
        /**
         * Where the hire stands: a phase they have already started, most recently touched first;
         * else the only phase that is open; else a real choice between the open phases. Locked
         * phases are never candidates.
         *
         * This used to be "the first phase with anything open, by position", which read a blueprint
         * as a line: a hire who finished phase 1 and picked phase 3 on their page was told to start
         * phase 2, and told it again when they said otherwise.
         *
         * A phase picked on the page but not started yet is invisible here -- the choice is page
         * state, not stored anywhere -- which is why [Choosing] tells the mentor to take the hire's
         * word for which phase they are doing.
         */
        fun of(phases: List<GetOnboardingPhaseForUserResponse>): PathStanding {
            val candidates = phases.filter { !it.locked && nextItemIn(it) != null }
            if (candidates.isEmpty()) {
                // Nothing reachable: either done, or everything left is locked or waiting on a skip
                // decision -- then the first unfinished phase is still what there is to talk about.
                return phases.firstOrNull { it.isOpen() }?.let { In(it) } ?: Finished
            }
            val started = candidates.filter { it.isStarted() }.maxByOrNull { it.lastActivity() }
            val pick = started ?: candidates.singleOrNull()
            return pick?.let { In(it) } ?: Choosing(candidates)
        }
    }
}

/**
 * One named next thing, in the two forms it is needed in.
 *
 * The greeting gets [plain] and the tool result gets [withIds], because an id in a greeting is an
 * identifier in front of the hire and an id missing from a tool result is an action the mentor
 * cannot offer.
 */
internal data class NextPathItem(
    val plain: String,
    val withIds: String,
)

/**
 * The item to do next in [phase], or null when the phase has nothing reachable.
 *
 * A step already in progress first -- it is where they left off -- and then the first open item in
 * the order the phase graph reads, steps and questions mixed, because a question is a node of the
 * same graph and often stands between two steps. [PhaseReadingOrder] is that order.
 *
 * This used to take every open step by position before any question, which named a step as next
 * while the page pointed at the question in front of it.
 */
internal fun nextItemIn(phase: GetOnboardingPhaseForUserResponse): NextPathItem? {
    val id = nextIdIn(phase) ?: return null
    phase.steps.firstOrNull { it.id == id }?.let { return stepItem(it) }
    val question = phase.questions.first { it.id == id }
    return NextPathItem(
        plain = "the question ${BuddyPathTools.quoted(question.question)}",
        withIds = "the question ${BuddyPathTools.quoted(question.question)} " +
            "[question_id: ${question.id}] [link: ${BuddyPathTools.QUESTION_LINK}${question.id}]",
    )
}

/**
 * The step whose checklist the mentor is shown: the step [nextItemIn] names, or null when that is a
 * question or nothing. Asked of the same rule, so the checklist in the tool result is always the
 * checklist of the thing it calls next.
 */
internal fun nextStepIn(phase: GetOnboardingPhaseForUserResponse): GetOnboardingStepsResponse? =
    nextIdIn(phase)?.let { id -> phase.steps.firstOrNull { it.id == id } }

/** The id behind [nextItemIn]: a step or a question of [phase], or null. */
private fun nextIdIn(phase: GetOnboardingPhaseForUserResponse): UUID? {
    if (phase.locked || !phase.isOpen()) return null

    val steps = phase.steps.sortedBy { it.position }
    val questions = phase.questions.sortedBy { it.position }
    steps.firstOrNull { it.status == StepStatus.IN_PROGRESS }?.let { return it.id }

    val stepsById = steps.associateBy { it.id }
    val questionsById = questions.associateBy { it.id }
    val order = PhaseReadingOrder.of(
        steps.map { it.id to it.blockerIds } + questions.map { it.id to it.blockerIds },
    )
    return order.firstOrNull { id ->
        stepsById[id]?.isReadyToStart() == true ||
            questionsById[id]?.let { it.status == QuestionStatus.OPEN || it.status == QuestionStatus.RETRY } == true
    }
}

private fun stepItem(step: GetOnboardingStepsResponse) = NextPathItem(
    plain = "the step ${BuddyPathTools.quoted(step.title)}",
    withIds = "the step ${BuddyPathTools.quoted(step.title)} [step_id: ${step.id}] " +
        "[link: ${BuddyPathTools.STEP_LINK}${step.id}]",
)

/**
 * Waiting, unlocked, and not asked to skip -- a step waiting on the PM's decision is not what to
 * tell them to do next.
 */
private fun GetOnboardingStepsResponse.isReadyToStart(): Boolean =
    status == StepStatus.WAITING && !locked && !hasPendingSkip()

/** Whether the hire asked to skip this step and their PM has not decided yet. */
internal fun GetOnboardingStepsResponse.hasPendingSkip(): Boolean = skip != null && skip.accepted == null

/** Whether a phase still has anything open: an unfinished step, or an unpassed question. */
internal fun GetOnboardingPhaseForUserResponse.isOpen(): Boolean {
    val openStep = steps.any { it.status != StepStatus.FINISHED && it.status != StepStatus.SKIPPED }
    return openStep || questions.any { it.status != QuestionStatus.PASSED }
}

/** Whether the hire has done anything in a phase yet: a step moved on, or a question answered. */
private fun GetOnboardingPhaseForUserResponse.isStarted(): Boolean =
    steps.any { it.status != StepStatus.WAITING } ||
        questions.any { it.status == QuestionStatus.PASSED || it.status == QuestionStatus.RETRY }

/** When the hire last did something in a phase, as epoch millis; 0 when never. */
private fun GetOnboardingPhaseForUserResponse.lastActivity(): Long =
    steps.flatMap { listOfNotNull(it.startedAt, it.completedAt) }.maxOfOrNull { it.toEpochMilli() } ?: 0L
