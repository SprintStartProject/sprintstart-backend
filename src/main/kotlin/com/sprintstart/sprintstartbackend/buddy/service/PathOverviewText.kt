package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.buddy.service.BuddyPathTools.Companion.NEWLINE
import com.sprintstart.sprintstartbackend.buddy.service.BuddyPathTools.Companion.PHASE_LINK
import com.sprintstart.sprintstartbackend.buddy.service.BuddyPathTools.Companion.quoted
import com.sprintstart.sprintstartbackend.path.model.response.path.GetOnboardingPathForUserResponse
import com.sprintstart.sprintstartbackend.path.model.response.phase.GetOnboardingPhaseForUserResponse

/*
 * How the hire's path as a whole reads to the mentor in get_my_onboarding_path: where they
 * stand, which phases they can choose between, what is ahead, and which came back empty.
 */

/** How many phase titles ahead are named, and how many empty phases. */
private const val AHEAD_SHOWN = 12

/** Where they are, how much of the path is behind them, and what else is open beside it. */
internal fun standing(
    phases: List<GetOnboardingPhaseForUserResponse>,
    current: GetOnboardingPhaseForUserResponse,
): String {
    // Phases behind them, never a percentage: one number over ticked steps, answered questions and empty
    // phases is nothing anybody could act on.
    val finished = phases.count { !it.isOpen() }
    val behind = when (finished) {
        0 -> "Nothing is behind them yet."
        1 -> "1 of them is behind them."
        else -> "$finished of them are behind them."
    }
    val alongside = phases.filter { it.id != current.id && !it.locked && it.isOpen() }
    val sideBySide = if (alongside.isEmpty()) {
        ""
    } else {
        " Phases are not done in number order, and these are open alongside it -- the hire may " +
            "switch to any of them whenever they like, so never tell them to finish a lower-numbered " +
            "phase first: " + alongside.joinToString(", ") { phaseRef(it, phases) } + "."
    }
    return "The hire's onboarding path has ${phases.size} phases. They are standing in phase " +
        "${phases.indexOf(current) + 1}, ${quoted(current.title)} -- the one they are working in. " +
        behind + sideBySide
}

/**
 * A fork: several phases open, none started, and the hire picks.
 *
 * Each option gets what it is for and where it starts, with the ids and link an action or a reply
 * needs, so "I'll take phase 3" can be answered about phase 3 straight away. Not the whole of each
 * phase: the hire is looking at the chooser on their page, and a mentor that recited every option
 * in full would have read the page out.
 */
internal fun StringBuilder.appendChoice(
    options: List<GetOnboardingPhaseForUserResponse>,
    phases: List<GetOnboardingPhaseForUserResponse>,
) {
    val finished = phases.count { !it.isOpen() }
    appendLine(
        "The hire's onboarding path has ${phases.size} phases, and $finished of them " +
            "${if (finished == 1) "is" else "are"} behind them. Several phases are open now and none " +
            "of them is started, so which one comes next is the hire's choice. Phases are not done in " +
            "number order: never tell them to take the lowest number first, and never pick one for " +
            "them -- say what each is for if they want help choosing. Their page asks them to " +
            "choose, and they may already have picked one there without starting anything in it: " +
            "if they say which phase they are doing, that is the phase they are in.",
    )
    appendLine()
    appendLine("Open to choose from:")
    options.forEach { phase ->
        appendLine("- ${phaseRef(phase, phases)} [phase_id: ${phase.id}]")
        phase.description.takeIf { it.isNotBlank() }?.let { appendLine("    · what it is for: $it") }
        nextItemIn(phase)?.let { appendLine("    · where it starts: ${it.withIds}") }
    }
    val locked = phases.filter { it.locked && it.isOpen() }
    if (locked.isNotEmpty()) {
        append(NEWLINE)
        appendLine("Still locked: " + locked.joinToString(", ") { quoted(it.title) })
    }
}

/** A phase named the way a reply should name it: its number, its title and its link. */
internal fun phaseRef(
    phase: GetOnboardingPhaseForUserResponse,
    phases: List<GetOnboardingPhaseForUserResponse>,
): String = "phase ${phases.indexOf(phase) + 1} ${quoted(phase.title)} [link: $PHASE_LINK${phase.id}]"

/** What else is left on the path, by title only. */
internal fun StringBuilder.appendAhead(
    phases: List<GetOnboardingPhaseForUserResponse>,
    current: GetOnboardingPhaseForUserResponse,
) {
    // Every unfinished phase but this one, lower numbers included: phases run side by side, so
    // "ahead" is what is left, not what comes after this one by position.
    val ahead = phases.filter { it.id != current.id && it.isOpen() }
    if (ahead.isEmpty()) return

    append(NEWLINE)
    appendLine(
        "Still left besides this phase. Titles only, and deliberately so: do not read this list " +
            "out, because the page they are on already lists it.",
    )
    ahead.take(AHEAD_SHOWN).forEach { phase ->
        val locked = if (phase.locked) " (locked until its blockers are done)" else " (open)"
        appendLine("- ${phases.indexOf(phase) + 1}. ${quoted(phase.title)}$locked")
    }
    if (ahead.size > AHEAD_SHOWN) appendLine("- and ${ahead.size - AHEAD_SHOWN} more")
}

/**
 * The phases that came back with nothing in them, and what a conversation may do about it.
 *
 * This is the one part of a path a conversation can genuinely repair. An AI-enhanced phase whose
 * project material was too thin is persisted honestly as empty rather than filled with invented
 * advice -- which is right, and leaves the hire with a phase that is a warning badge. Its title
 * and description still say what it was *meant* to cover, and that is enough to talk about.
 */
internal fun StringBuilder.appendEmptyPhases(path: GetOnboardingPathForUserResponse) {
    val issues = path.generationIssues
    if (issues.isEmpty()) return

    append(NEWLINE)
    appendLine(
        "These phases came back with nothing in them, because the project's own material did " +
            "not support them:",
    )
    issues.take(AHEAD_SHOWN).forEach { issue ->
        append("- ${quoted(issue.title)} (${issue.status}) [phase_id: ${issue.phaseId}]")
        appendLine(
            issue.description
                .takeIf { it.isNotBlank() }
                ?.let { ": $it" }
                .orEmpty(),
        )
    }
    appendLine(
        "That is not the hire's fault and not something trying again fixes. Their titles and " +
            "descriptions say what each was meant to cover, so they are subjects you can talk " +
            "through -- and if something concrete comes out of that conversation, offer " +
            "add_path_step with that phase_id so the phase stops being empty. Once it has a step, " +
            "the phase shows up on their path.",
    )
}
