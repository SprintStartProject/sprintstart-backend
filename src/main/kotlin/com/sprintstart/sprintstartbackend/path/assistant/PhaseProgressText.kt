package com.sprintstart.sprintstartbackend.path.assistant

import com.sprintstart.sprintstartbackend.path.assistant.MyPathTool.Companion.ITEMS_SHOWN
import com.sprintstart.sprintstartbackend.path.assistant.MyPathTool.Companion.NEWLINE
import com.sprintstart.sprintstartbackend.path.assistant.MyPathTool.Companion.STEP_LINK
import com.sprintstart.sprintstartbackend.path.assistant.MyPathTool.Companion.quoted
import com.sprintstart.sprintstartbackend.path.external.enums.QuestionStatus
import com.sprintstart.sprintstartbackend.path.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTaskResponse
import java.util.UUID

/*
 * What the hire can do next in the phase they are in, as the mentor reads it: steps whose
 * checklist is done but that are still open, the next step's checklist, and the next item.
 */

/**
 * The steps whose every checklist line is ticked while the step itself is still open.
 *
 * The most common way a hire gets stuck without knowing it: they did the work, ticked every line,
 * and never pressed the step's own button -- so whatever waits on the step stays locked, and the
 * page gives no reason. A checklist that is done is not a finished step (the product keeps the
 * two apart on purpose), which is exactly why this is something to *ask* about rather than
 * something to assume. A step with no checklist is never on this list: there is nothing that
 * says it is done.
 */
internal fun readyToClose(
    phase: GetOnboardingPhaseForUserResponse,
    checklists: Map<UUID, List<GetOnboardingTaskResponse>>,
): List<GetOnboardingStepsResponse> =
    phase.steps
        .sortedBy { it.position }
        // Not a step they asked to skip: pushing them to finish it would withdraw the request.
        .filterNot { it.hasPendingSkip() }
        .filter { step -> checklists[step.id]?.let { it.isNotEmpty() && it.all { task -> task.finished } } == true }

/**
 * What finishing [step] would open up, named: the items in its phase that wait on it, and --
 * when it is the last open thing in the phase -- the phases that wait on the phase.
 *
 * Named because "it unlocks the next thing" is a reason nobody can check, and "it is what #3
 * waits on" is one the hire can see on their page.
 */
internal fun unlockedBy(
    step: GetOnboardingStepsResponse,
    phase: GetOnboardingPhaseForUserResponse,
    phases: List<GetOnboardingPhaseForUserResponse>,
    numbers: Map<UUID, Int>,
): List<String> {
    val inPhase = phase.steps
        .filter { step.id in it.blockerIds && it.id != step.id }
        .map { "#${numbers[it.id]} ${quoted(it.title)}" } +
        phase.questions
            .filter { step.id in it.blockerIds }
            .map { "#${numbers[it.id]} ${quoted(it.question)}" }

    // Locked steps count as open here: one waiting on this step is still between it and the end.
    val lastOpen = phase.steps.none {
        it.id != step.id && it.status != StepStatus.FINISHED && it.status != StepStatus.SKIPPED
    } &&
        phase.questions.all { it.status == QuestionStatus.PASSED }
    val laterPhases = if (lastOpen) {
        phases.filter { phase.id in it.blockerIds }.map { "the phase ${quoted(it.title)}" }
    } else {
        emptyList()
    }
    return inPhase + laterPhases
}

/**
 * Steps they have done the work of but not closed, with what each is holding up.
 *
 * Addressed to the mentor with an instruction, because this is the one case where it should bring
 * a completion up *unprompted*: a hire who does not know why their next step is locked is not
 * going to ask about the step that is locking it.
 */
internal fun StringBuilder.appendReadyToClose(
    phase: GetOnboardingPhaseForUserResponse,
    phases: List<GetOnboardingPhaseForUserResponse>,
    checklists: Map<UUID, List<GetOnboardingTaskResponse>>,
) {
    val ready = readyToClose(phase, checklists)
    if (ready.isEmpty()) return

    val numbers = numbering(phase.steps.sortedBy { it.position }, phase.questions.sortedBy { it.position })
    append(NEWLINE)
    appendLine(
        "READY TO CLOSE -- every line of these checklists is ticked, but the step itself is still " +
            "open, so nothing that waits on it has unlocked:",
    )
    ready.take(ITEMS_SHOWN).forEach { step ->
        val waiting = unlockedBy(step, phase, phases, numbers)
        val holding = if (waiting.isEmpty()) "" else " -- it is what ${waiting.joinToString(", ")} waits on"
        appendLine(
            "- #${numbers[step.id]} ${quoted(step.title)} [step_id: ${step.id}] " +
                "[link: $STEP_LINK${step.id}]$holding",
        )
    }
    appendLine(
        "This step is where they actually are, and it comes before anything else that is open. " +
            "When they ask what is next, where they are or why something is locked, answer in this " +
            "order: (1) they are on this step, and its checklist is all ticked; (2) what finishing " +
            "it opens, by number; (3) ask whether they are done with it -- and call complete_step " +
            "for it in that same reply so the button is there. Keep it light: do not lead with the " +
            "button, and do not lecture them about being sure. Otherwise mention it in a sentence at " +
            "the end of your answer. Once is enough: if they say not yet, leave it.",
    )
}

/**
 * The checklist of the step the hire is actually on, with the id of each line.
 *
 * Only for the one step, because this is the level where a conversation happens -- "I have done
 * the first two, the third one is where I am stuck" -- and putting every step's checklist in the
 * prompt would bury the path it is meant to describe.
 *
 * Read by step id rather than through an authorizing read, because the step came out of the
 * hire's own path a moment ago: the resolution in [MyPathTool.findStep] is what proves it is theirs, and
 * doing it twice would not make it truer.
 */
internal fun StringBuilder.appendCurrentTasks(
    step: GetOnboardingStepsResponse?,
    checklists: Map<UUID, List<GetOnboardingTaskResponse>>,
) {
    if (step == null) return
    val tasks = checklists[step.id].orEmpty()
    if (tasks.isEmpty()) return

    append(NEWLINE)
    appendLine("The checklist of ${quoted(step.title)}, the step they are on:")
    tasks.take(ITEMS_SHOWN).forEach { task ->
        val mark = if (task.finished) "done" else "open"
        appendLine("- [$mark] ${quoted(task.title)} [task_id: ${task.id}]")
    }
    if (tasks.size > ITEMS_SHOWN) appendLine("- and ${tasks.size - ITEMS_SHOWN} more")
    // Said here because it is where the mentor will be tempted otherwise: the step's own
    // completion is not the sum of its checklist, and the product allows both.
    appendLine(
        "Ticking these off is complete_task. A step can be finished with lines still open, so " +
            "never tell them the checklist has to be empty first -- and never tick a line off " +
            "because the conversation covered it.",
    )
}

/**
 * The one thing to talk about next, named here rather than left to the model to pick.
 *
 * A step whose checklist is done but that was never closed comes first: it is where they
 * actually are, and whatever the page calls next is often locked behind it.
 */
internal fun StringBuilder.appendNextItem(
    phase: GetOnboardingPhaseForUserResponse,
    ready: GetOnboardingStepsResponse?,
) {
    append(NEWLINE)
    if (ready != null) {
        appendLine(
            "The next thing waiting for them: finishing the step ${quoted(ready.title)} " +
                "[step_id: ${ready.id}] [link: $STEP_LINK${ready.id}], whose checklist is already done.",
        )
        return
    }
    when (val next = nextItemIn(phase)) {
        null -> appendLine("Nothing in this phase is open right now.")
        else -> appendLine("The next thing waiting for them: ${next.withIds}.")
    }
}
