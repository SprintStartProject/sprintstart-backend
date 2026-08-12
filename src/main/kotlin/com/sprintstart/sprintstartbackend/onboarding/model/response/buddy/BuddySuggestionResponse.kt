package com.sprintstart.sprintstartbackend.onboarding.model.response.buddy

/**
 * One thing this hire could usefully ask their buddy, offered as a chip beside the composer.
 *
 * ⚠️ Exists because a capability reachable only by knowing what to type is one a hire never uses.
 * Somebody who does not know an action exists, or does not know the chat is how you reach it,
 * never triggers it.
 *
 * [question] is put in the composer and **never sent**: the hire presses send. The chip removes the
 * guesswork about vocabulary, not the hire's authorship of the question. [label] is what the chip
 * says, kept short enough to read at a glance in a 384 px panel.
 */
data class BuddySuggestionResponse(
    val label: String,
    val question: String,
)
