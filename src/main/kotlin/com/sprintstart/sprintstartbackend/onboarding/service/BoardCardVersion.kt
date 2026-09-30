package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCardPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ChecklistPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.LinkPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.NotePayload
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.LinkContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.NoteContent
import java.security.MessageDigest

/**
 * A fingerprint of the words on an authored card, for telling that it is still the card a buddy
 * proposal was made against.
 *
 * One canonical form for both sides of the comparison: the proposal fingerprints the card as the
 * board read it ([of] on the response content), and the confirm fingerprints the row it holds the
 * lock on ([of] on the stored payload). Ticks are left out on purpose: an edit keeps them, so
 * ticking a line between the offer and the click changes nothing the confirm showed.
 */
internal object BoardCardVersion {
    fun of(content: BoardCardContent?): String? = when (content) {
        is NoteContent -> digest(listOf("note", content.text))
        is LinkContent -> digest(listOf("link", content.url, content.label.orEmpty()))
        is ChecklistContent -> digest(listOf("checklist", content.title.orEmpty()) + content.items.map { it.text })
        else -> null
    }

    fun of(payload: BoardCardPayload?): String? = when (payload) {
        is NotePayload -> digest(listOf("note", payload.text))
        is LinkPayload -> digest(listOf("link", payload.url, payload.label.orEmpty()))
        is ChecklistPayload -> digest(listOf("checklist", payload.title.orEmpty()) + payload.items.map { it.text })
        else -> null
    }

    const val CARD_CHANGED =
        "That card changed after this was proposed, so nothing was written. Ask your buddy to look at it again."
    const val NOTE_TOO_LONG_TO_REPLACE =
        "That note is longer than the buddy can read, so replacing it whole would lose what it never saw. " +
            "Edit it yourself instead."

    private fun digest(words: List<String>): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(words.joinToString("\u0000").toByteArray())
            .joinToString("") { "%02x".format(it) }
}
