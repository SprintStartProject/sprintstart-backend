package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.question.GetOnboardingQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyPathTools.Companion.QUESTION_LINK
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyPathTools.Companion.STEP_LINK
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyPathTools.Companion.STEP_PAGE_LINK
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyPathTools.Companion.quoted
import java.util.UUID

/*
 * How one step or knowledge-check question of the hire's path reads to the mentor in
 * get_my_onboarding_path: where it stands, what it waits on and what finishing it opens.
 */

/** How many expected outcomes of one step are quoted. Enough to say what "done" means. */
private const val OUTCOMES_SHOWN = 3

/** One step: what it is, where it stands, and the ids and link an action or a reply needs. */
internal fun StringBuilder.appendStep(
    step: GetOnboardingStepsResponse,
    graph: PhaseGraph,
) {
    val numbers = graph.numbers
    val state = when {
        step.status == StepStatus.FINISHED -> "done"
        step.status == StepStatus.SKIPPED -> "skipped"
        step.hasPendingSkip() -> "SKIP REQUESTED, waiting on their PM"
        step.locked -> "LOCKED, cannot be started yet"
        step.status == StepStatus.IN_PROGRESS -> "started"
        else -> "open"
    }
    appendLine(
        "- #${numbers[step.id]} [$state] ${quoted(step.title)} (${step.estimatedMinutes} min) " +
            "[step_id: ${step.id}] [link: $STEP_LINK${step.id}]",
    )
    step.description.takeIf { it.isNotBlank() }?.let { appendLine("    · $it") }
    step.expectedOutcomes.take(OUTCOMES_SHOWN).forEach {
        appendLine("    · should leave them able to: $it")
    }
    appendEdges(step.id, step.locked, step.blockerIds, graph)
    appendSkip(step)
}

/**
 * Where a skip request for [step] stands, and what that means for the mentor.
 *
 * A skip already asked for is the one thing about a step whose state is nowhere else in this
 * text, and a mentor that cannot see it will offer to request a second one -- or push the hire
 * to finish a step whose skip is waiting on their PM, which withdraws the request.
 */
internal fun StringBuilder.appendSkip(step: GetOnboardingStepsResponse) {
    step.skip?.let { skip ->
        val verdict = when (skip.accepted) {
            true -> "their PM accepted it"
            false -> "their PM declined it"
            null -> "nobody has decided yet"
        }
        appendLine("    · they asked to skip this ($verdict): ${quoted(skip.reason)}")
        // The PM's own words are the most useful thing about a decision, and a declined request
        // is exactly when the hire will want to talk about why.
        skip.reviewComment?.takeIf { it.isNotBlank() }?.let {
            appendLine("    · their PM's comment on it: ${quoted(it)}")
        }
        if (skip.accepted == null) {
            appendLine(
                "    · while it is pending, do not push them to do this step, and do not offer " +
                    "complete_step for it unless they say they did it anyway: finishing the step " +
                    "withdraws the request. They can change or withdraw the reason on the step's " +
                    "own page [page: $STEP_PAGE_LINK${step.id}].",
            )
        }
    }
}

/**
 * One question, as the hire sees it -- and no further.
 *
 * The options are listed because the hire is looking at them, and a mentor that cannot name them
 * has to ask the hire to read their own screen out. Which one is right is not here, and the
 * absence is the feature: see the class comment.
 */
internal fun StringBuilder.appendQuestion(
    question: GetOnboardingQuestionForUserResponse,
    phase: GetOnboardingPhaseForUserResponse,
    graph: PhaseGraph,
    lastAnswer: String?,
) {
    val numbers = graph.numbers
    val state = when (question.status) {
        QuestionStatus.PASSED -> "passed"
        QuestionStatus.RETRY -> "answered wrong before, still open"
        QuestionStatus.LOCKED -> "LOCKED, cannot be answered yet"
        QuestionStatus.OPEN -> "open"
    }
    appendLine(
        "- #${numbers[question.id]} [$state] ${quoted(question.question)} (${question.type}) " +
            "[question_id: ${question.id}] [link: $QUESTION_LINK${question.id}]",
    )
    val options = question.options.sortedBy { it.position }
    if (options.isNotEmpty()) {
        appendLine("    · the options they see: " + options.joinToString("; ") { it.label })
        // Hinting is answering: matching an option to a title gives the question away just the same.
        appendLine(
            "    · never narrow these down for them: not by pointing at an option that matches a " +
                "title or wording elsewhere, not by ruling any out, and not by saying how close a " +
                "wrong answer was -- you do not know.",
        )
    }
    appendEdges(question.id, question.status == QuestionStatus.LOCKED, question.blockerIds, graph)
    // A wrong answer is the clearest signal on the whole path that a step did not land. Teaching
    // the material in the conversation comes first; a refresher step is for when what they
    // missed is more than one explanation, so it is still there tomorrow.
    if (question.status == QuestionStatus.RETRY) {
        // What they actually said, so the tutoring can start from what did not land rather than
        // from the whole of the material. Their own answer, never the correct one.
        lastAnswer?.let {
            appendLine(
                "    · their last answer, which was not right: ${quoted(it)}. Start from what it " +
                    "shows they understood and where it went wrong -- without saying what the " +
                    "right answer is.",
            )
        }
        // Placed before the question, so the refresher is what opens it, and after what the
        // question waits on now -- or, when that is nothing, after where the hire is. The same
        // inference the action applies, spelled out so the mentor passes both halves.
        val waitsOn = PathStepPlacement.inferred(phase, emptySet(), setOf(question.id)).waitsOn.joinToString(", ")
        appendLine(
            "    · they got this wrong before, so the material behind it did not land. Go through it " +
                "with them first. If what they missed is bigger than one explanation, offer " +
                "add_path_step for one short refresher step in this phase [phase_id: ${phase.id}] " +
                "that says what to revisit and where -- never the answer. Put it in front of this " +
                "question: unlocks = [${question.id}], waits_on = [$waitsOn].",
        )
    }
}

/**
 * Where an item sits in its phase's graph: what it comes after, whether that has locked it, and
 * what finishing it opens.
 *
 * Every item, not only locked ones. Told only about locks, the mentor read the numbers as a
 * sequence and told a hire "after #6 comes #7" about items that do not depend on each other at all
 * -- and could not place a new step anywhere but the end, because it had never seen an edge.
 * A lock that comes from the phase itself is stated once above and said so here, rather than
 * repeating the phase's own blockers on every line.
 */
internal fun StringBuilder.appendEdges(
    id: UUID,
    locked: Boolean,
    blockerIds: Set<UUID>,
    graph: PhaseGraph,
) {
    val named = blockerIds.mapNotNull { blocker ->
        graph.titles[blocker]?.let { title -> "#${graph.numbers[blocker]} " + quoted(title) }
    }
    when {
        named.isNotEmpty() && locked ->
            appendLine(
                "    · comes after ${named.joinToString(", ")}, which is not finished yet -- so it is " +
                    "locked. Do not offer to start it before then.",
            )
        named.isNotEmpty() -> appendLine("    · comes after ${named.joinToString(", ")}")
        locked -> appendLine("    · locked by this phase, not by anything inside it. Do not offer to start it.")
    }
    val opens = graph.opens[id].orEmpty().mapNotNull { next -> graph.numbers[next]?.let { "#$it" } }
    if (opens.isNotEmpty()) appendLine("    · opens: ${opens.joinToString(", ")}")
}
