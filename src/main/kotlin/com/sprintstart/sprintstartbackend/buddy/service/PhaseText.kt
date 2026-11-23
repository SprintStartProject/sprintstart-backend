package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.buddy.service.BuddyPathTools.Companion.ITEMS_SHOWN
import com.sprintstart.sprintstartbackend.buddy.service.BuddyPathTools.Companion.PHASE_LINK
import com.sprintstart.sprintstartbackend.buddy.service.BuddyPathTools.Companion.quoted
import com.sprintstart.sprintstartbackend.path.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.GetOnboardingStepsResponse
import java.util.UUID

/*
 * How the phase the hire is in reads to the mentor in get_my_onboarding_path: its steps and
 * questions numbered the way their page numbers them, and what finishing each one opens.
 */

/**
 * The phase they are in, in full: what it is for, its steps, and its questions.
 *
 * Every item carries three things beyond its own text, each for a reason a testing session made
 * obvious:
 *
 * - **A number**, the same number the hire's page prints on the card. It is what lets them say
 *   "let's do 3" instead of retyping a title, and it only works because both sides derive it the
 *   same way: steps in position order, then questions in position order (see [numbering]).
 * - **A link**, so "want to take the check?" can arrive as something clickable rather than as an
 *   instruction to go and find it.
 * - **What a locked item is waiting on, by name.** "Locked" on its own left the mentor telling a
 *   hire they could go ahead and do a step the page would not let them open.
 */
internal fun StringBuilder.appendCurrentPhase(
    phase: GetOnboardingPhaseForUserResponse,
    index: Int,
    phases: List<GetOnboardingPhaseForUserResponse>,
    lastAnswers: Map<UUID, String> = emptyMap(),
) {
    appendLine(
        "Phase ${index + 1} of ${phases.size}: ${quoted(phase.title)} " +
            "[phase_id: ${phase.id}] [link: $PHASE_LINK${phase.id}]",
    )
    phase.description.takeIf { it.isNotBlank() }?.let { appendLine("What it is for: $it") }
    if (phase.locked) {
        val waiting = phase.blockerIds.mapNotNull { id -> phases.firstOrNull { it.id == id } }
        appendLine(
            "This whole phase is locked, so nothing in it can be started yet. It waits on: " +
                waiting.joinToString(", ") { quoted(it.title) }.ifBlank { "an earlier phase" },
        )
    }

    val steps = phase.steps.sortedBy { it.position }
    val questions = phase.questions.sortedBy { it.position }

    if (steps.isEmpty() && questions.isEmpty()) {
        appendLine(
            "This phase has nothing in it -- no steps, no questions. Nothing was generated for " +
                "it, and waiting will not change that.",
        )
        return
    }

    val numbers = numbering(steps, questions)
    val titles = titlesIn(phase)
    val graph = PhaseGraph(numbers, titles, opensIn(phase))

    appendGraphIntro(phase, steps, questions, numbers)

    if (steps.isNotEmpty()) {
        appendLine("Steps, numbered as their page shows them:")
        steps.take(ITEMS_SHOWN).forEach { appendStep(it, graph) }
        if (steps.size > ITEMS_SHOWN) appendLine("- and ${steps.size - ITEMS_SHOWN} more")
    }

    if (questions.isNotEmpty()) {
        appendLine(
            "Knowledge questions. They count like steps, so a phase whose steps are done and " +
                "whose questions are unanswered is still the phase they are standing in:",
        )
        questions.take(ITEMS_SHOWN).forEach { appendQuestion(it, phase, graph, lastAnswers[it.id]) }
        if (questions.size > ITEMS_SHOWN) appendLine("- and ${questions.size - ITEMS_SHOWN} more")
    }
}

/**
 * The number each item of a phase carries, keyed by id.
 *
 * Steps first in position order, then questions in position order — which is the order the hire's
 * own page lists them in, and that is the whole point: a number only helps if the thing they say
 * and the thing the mentor hears are the same item. Position alone would not do it, because
 * steps and questions are numbered from the same sequence on screen but carry their own
 * positions underneath.
 */
internal fun numbering(
    steps: List<GetOnboardingStepsResponse>,
    questions: List<GetOnboardingQuestionForUserResponse>,
): Map<UUID, Int> =
    (steps.map { it.id } + questions.map { it.id })
        .withIndex()
        .associate { (index, id) -> id to index + 1 }

/** How to read the phase's items as a graph, and which of them are open right now. */
internal fun StringBuilder.appendGraphIntro(
    phase: GetOnboardingPhaseForUserResponse,
    steps: List<GetOnboardingStepsResponse>,
    questions: List<GetOnboardingQuestionForUserResponse>,
    numbers: Map<UUID, Int>,
) {
    appendLine(
        "The items of a phase form a dependency graph, not a list: an item opens once everything " +
            "it comes after is done, several can be open at the same time, and finishing one can " +
            "open several at once. Each item below says what it comes after and what it opens. " +
            "Talk about it that way -- never \"after #6 comes #7\" unless #7 really comes after #6.",
    )
    val openNow = steps.filter { it.isFinishable() && !it.hasPendingSkip() }.map { it.id } +
        questions.filter { it.status == QuestionStatus.OPEN || it.status == QuestionStatus.RETRY }.map { it.id }
    if (!phase.locked && openNow.isNotEmpty()) {
        appendLine("Open right now: " + openNow.joinToString(", ") { "#${numbers[it]}" })
    }
    // Both halves of "add a step as the next thing" are worked out, so the mentor passes what the step
    // comes after as well as what it unlocks.
    PathStepPlacement.anchorOf(phase)?.let { anchor ->
        val done = steps
            .filter { it.status == StepStatus.FINISHED || it.status == StepStatus.SKIPPED }
            .map { it.id }
            .toSet() + questions.filter { it.status == QuestionStatus.PASSED }.map { it.id }
        val after = opensIn(phase)[anchor].orEmpty().filterNot { it in done }
        appendLine(
            "To add a step as the next thing after #${numbers[anchor]}, where they are: pass BOTH " +
                "waits_on = [$anchor] and unlocks = [${after.joinToString(", ")}].",
        )
    }
}

/**
 * For every item of [phase], the items that come directly after it -- the edges the graph view
 * draws, read the other way round, because "finishing this opens #3 and #4" is the sentence a
 * hire can act on and the data only stores "#3 comes after this".
 */
internal fun opensIn(phase: GetOnboardingPhaseForUserResponse): Map<UUID, List<UUID>> {
    val edges = phase.steps.map { it.id to it.blockerIds } + phase.questions.map { it.id to it.blockerIds }
    return edges
        .flatMap { (item, blockers) -> blockers.map { it to item } }
        .groupBy({ it.first }, { it.second })
}

/** Every item of a phase by id, so a blocker can be named rather than counted. */
internal fun titlesIn(phase: GetOnboardingPhaseForUserResponse): Map<UUID, String> =
    phase.steps.associate { it.id to it.title } + phase.questions.associate { it.id to it.question }

/** Whether a step can still be finished: not locked, and neither finished nor skipped. */
internal fun GetOnboardingStepsResponse.isFinishable(): Boolean =
    !locked && (status == StepStatus.WAITING || status == StepStatus.IN_PROGRESS)
