package com.sprintstart.sprintstartbackend.path.assistant

import com.sprintstart.sprintstartbackend.path.assistant.MyPathCopy.CLOSING
import com.sprintstart.sprintstartbackend.path.assistant.MyPathCopy.FINISHED_PATH
import com.sprintstart.sprintstartbackend.path.assistant.MyPathCopy.FINISHED_VISIBLE
import com.sprintstart.sprintstartbackend.path.assistant.MyPathCopy.NO_PHASES
import com.sprintstart.sprintstartbackend.path.assistant.MyPathCopy.ONLY_EMPTY_PHASES
import com.sprintstart.sprintstartbackend.path.assistant.MyPathTool.Companion.NEWLINE
import com.sprintstart.sprintstartbackend.path.assistant.MyPathTool.Companion.quoted
import com.sprintstart.sprintstartbackend.path.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.task.GetOnboardingTaskResponse
import java.util.Locale
import java.util.UUID

/**
 * What the mentor reads about a path that has no visible phase.
 *
 * Nothing visible is not the same as nothing there: every phase may have come back empty from
 * generation, and those are hidden from the path's phases but are exactly what add_path_step
 * repairs. Only a path with no phases at all is the authoring problem [NO_PHASES] describes.
 */
internal fun noVisiblePhasesReading(path: GetOnboardingPathForUserResponse): String {
    if (path.generationIssues.isEmpty()) return NO_PHASES
    return buildString {
        append(ONLY_EMPTY_PHASES)
        appendEmptyPhases(path)
        append(NEWLINE + CLOSING)
    }
}

/** What the mentor reads once every visible phase is finished. */
internal fun finishedReading(
    path: GetOnboardingPathForUserResponse,
    phases: List<GetOnboardingPhaseForUserResponse>,
): String {
    // A path whose phases all came back empty was never started, let alone finished: say what is
    // actually there, which is nothing, rather than congratulate them.
    if (phases.all { it.steps.isEmpty() && it.questions.isEmpty() }) {
        return buildString {
            appendCurrentPhase(phases.last(), phases.lastIndex, phases)
            appendEmptyPhases(path)
        }
    }
    if (path.generationIssues.isEmpty()) return FINISHED_PATH.format(Locale.ROOT, phases.size)
    // Finished with what is visible, not with the path: phases that came back empty are still there
    // to be filled, and "nothing left on it" would close the one door to that.
    return buildString {
        append(FINISHED_VISIBLE.format(Locale.ROOT, phases.size))
        appendEmptyPhases(path)
    }
}

/** What the mentor reads when several phases are open and none is started: the hire chooses. */
internal fun choosingReading(
    path: GetOnboardingPathForUserResponse,
    phases: List<GetOnboardingPhaseForUserResponse>,
    open: List<GetOnboardingPhaseForUserResponse>,
): String =
    buildString {
        appendChoice(open, phases)
        appendEmptyPhases(path)
        append(NEWLINE + CLOSING)
    }

/** What the mentor reads about the phase the hire is standing in. */
internal fun inPhaseReading(
    path: GetOnboardingPathForUserResponse,
    current: GetOnboardingPhaseForUserResponse,
    checklists: Map<UUID, List<GetOnboardingTaskResponse>>,
    lastWrongAnswers: Map<UUID, String>,
): String {
    val phases = path.phases.sortedBy { it.position }
    return buildString {
        appendLine(standing(phases, current))
        appendLine()
        appendCurrentPhase(current, phases.indexOf(current), phases, lastWrongAnswers)
        appendReadyToClose(current, phases, checklists)
        appendCurrentTasks(nextStepIn(current), checklists)
        appendNextItem(current, readyToClose(current, checklists).firstOrNull())
        appendAhead(phases, current)
        appendEmptyPhases(path)
        append(NEWLINE + CLOSING)
    }
}

/** The greeting snapshot of a path with no visible phase. */
internal fun noVisiblePhasesSnapshot(path: GetOnboardingPathForUserResponse): String {
    val empty = path.generationIssues
    return if (empty.isEmpty()) {
        "Onboarding path:\nTheir path has no phases in it, so there is nothing on it to do yet."
    } else {
        "Onboarding path:\nEvery one of their ${empty.size} phases came back with nothing in it, so " +
            "there is nothing on it to do yet -- but those phases can be worked out together and " +
            "filled in."
    }
}

/** The greeting line for a path whose phases are all finished. */
internal fun finishedSnapshotLine(path: GetOnboardingPathForUserResponse): String =
    if (path.generationIssues.isEmpty()) {
        "They have finished every phase of their path."
    } else {
        "They have finished every phase of their path that has something in it."
    }

/** The greeting line for several open, unstarted phases. */
internal fun choosingSnapshotLine(open: List<GetOnboardingPhaseForUserResponse>): String =
    "${open.size} phases are open to them and nothing in them is started yet: " +
        open.joinToString(", ") { quoted(it.title) } + ". Which one comes next is " +
        "their choice -- phases are not taken in number order."

/** The greeting lines for the phase the hire is in: where they are, what is ready to close, what is next. */
internal fun StringBuilder.appendInPhaseSnapshot(
    phases: List<GetOnboardingPhaseForUserResponse>,
    current: GetOnboardingPhaseForUserResponse,
    checklists: Map<UUID, List<GetOnboardingTaskResponse>>,
) {
    appendLine(
        "They are in phase ${phases.indexOf(current) + 1} of ${phases.size}: " +
            "${quoted(current.title)}.",
    )
    // First, when there is one: a step whose checklist is done but that was never closed is what a
    // greeting can usefully open on, because it is what is quietly holding everything after it.
    readyToClose(current, checklists).firstOrNull()?.let {
        appendLine(
            "Every line of the checklist of ${quoted(it.title)} is ticked, but the step " +
                "itself is still open, so what comes after it has not unlocked. It is " +
                "worth asking whether they are done with it.",
        )
    }
    nextItemIn(current)?.let { appendLine("The next thing waiting for them is ${it.plain}.") }
}
