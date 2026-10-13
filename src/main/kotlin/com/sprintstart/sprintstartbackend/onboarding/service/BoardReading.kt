package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardStage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructurePayload
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.LinkContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.NoteContent

/**
 * Reading a board the way the hire sees it.
 *
 * The client has always derived this — what a card is called, whether it is finished, whether it is
 * waiting on something — and kept it to itself, because the arrangement it derives from lived in one
 * browser. Now that the arrangement is on the server, anything server-side that wants to talk about
 * the board sensibly needs the same three answers, and the buddy is the first such thing.
 *
 * **This is a second implementation of rules the client also has, and that is a cost worth naming.**
 * The alternative was the client sending its conclusions along with every question, which makes the
 * buddy's view of the board something the page can misreport. The derivation is small and the inputs
 * are the same stored facts, so two readers agreeing is a matter of keeping ten lines in step. If
 * they ever drift, this file is the one that decides what the *buddy* believes — and the buddy is
 * the one that has to be right, because it says things out loud.
 */
object BoardReading {
    /**
     * How many lines of one checklist the mentor is shown.
     *
     * Enough to work with a real list, and a cap because a board of ten checklists would otherwise
     * put a few hundred lines in every prompt. What is left out is counted rather than dropped
     * silently, so the mentor can say it does not have the whole list instead of assuming it does.
     */
    private const val LINES_PER_CARD = 12

    /**
     * How much of a note the mentor is shown. Enough to edit a real note from; a runaway one is cut
     * and marked, so the mentor knows it does not have all of it before proposing to rewrite it.
     */
    const val NOTE_PREVIEW = 1500

    /**
     * Every card on the board with the id the board tools need, and what the hire's own cards say,
     * or "" for an empty board.
     *
     * Ids appear in this one section and nowhere else in a board read. Every other line of that
     * read is written to be *said* — names, counts, what waits on what — and an id sitting in one
     * of those is a thing the mentor ends up reading out to somebody who cannot use it.
     *
     * Every card rather than only the checklists, since the buddy may now propose to edit a note or
     * a link, take any card off, or rearrange the board — and each of those needs the id of a card
     * it could not otherwise name. The hire's own cards carry their words as well: a note cannot be
     * rewritten by somebody who cannot see what it says now.
     *
     * Built here rather than in `BuddyBoardTools.readBoard` so that function gains no branch: it
     * is already at detekt's complexity ceiling, and a board read is exactly the kind of function
     * that grows a condition per release until nobody can follow it.
     */
    fun cardsSection(cards: List<BoardCardResponse>, limit: Int): String {
        if (cards.isEmpty()) return ""

        return buildString {
            append("\n\nEvery card, in board order, with its id. The ids are for the board tools only ")
            append("— never say one to the hire. Their own cards show what is on them: add steps ")
            append("with amend_checklist rather than making a second card beside one, and tick ")
            append("lines off with tick_checklist_items when they say they have done them — both ")
            append("match by the words below, so quote them exactly:")
            cards.take(limit).forEach { card ->
                append("\n- " + nameOf(card) + " (" + describe(card) + ", id: " + card.id + ")")
                append(contentOf(card))
            }
            if (cards.size > limit) append("\n- and ${cards.size - limit} more, not listed")
        }
    }

    /** What kind of card this is, and whose, in a few words. */
    private fun describe(card: BoardCardResponse): String {
        val kind = card.kind.name
            .lowercase()
            .replace('_', ' ')
        return if (card.owner == BoardCardOwner.HIRE) "their $kind" else kind
    }

    /** The words on one of the hire's own cards, capped; nothing for a live card. */
    private fun contentOf(card: BoardCardResponse): String =
        when (val content = card.content) {
            is ChecklistContent -> lines(card)
            is NoteContent -> "\n    " + content.text.take(NOTE_PREVIEW).replace("\n", "\n    ") +
                if (content.text.length > NOTE_PREVIEW) "…" else ""
            is LinkContent -> "\n    " + content.url
            else -> ""
        }

    /**
     * One checklist's lines, ticked or not, capped.
     *
     * The lines and not only a count, because both write tools match on the words: a mentor that
     * knows a list has seven things but not what they say can neither add the eighth without
     * repeating one nor tick the second.
     *
     * Capped per card rather than only across the board — one runaway list would otherwise fill
     * the prompt on its own — and open lines first, since those are the ones anything is going to
     * be done to.
     */
    private fun lines(card: BoardCardResponse): String {
        val content = card.content as? ChecklistContent ?: return ""
        val ordered = content.items.sortedBy { it.done }

        return buildString {
            ordered.take(LINES_PER_CARD).forEach { item ->
                append("\n    " + (if (item.done) "[x] " else "[ ] ") + item.text)
            }
            val hidden = ordered.size - LINES_PER_CARD
            if (hidden > 0) append("\n    (" + hidden + " more, not listed)")
        }
    }

    /** What a card is called, in the words the hire would use for it. */
    fun nameOf(card: BoardCardResponse): String =
        when (val content = card.content) {
            is NoteContent -> firstLine(content.text)
            is LinkContent -> content.label?.takeIf { it.isNotBlank() } ?: content.url
            is ChecklistContent -> content.title?.takeIf { it.isNotBlank() } ?: "a checklist"
            // The live kinds have no title of their own: their name is what they are. The enum's
            // own word is turned into a phrase rather than shown as `OPEN_PULL_REQUESTS`, which is
            // a thing to decode rather than to read.
            else -> {
                val word = card.kind.name
                word.lowercase().replace('_', ' ')
            }
        }

    /**
     * Whether a card is finished.
     *
     * Observed where it can be observed, ticked where it cannot — the same split the client makes. An
     * empty checklist is deliberately *not* finished: zero of zero is a list nobody has written yet,
     * and calling it done would let a blank card unblock everything behind it.
     */
    fun isDone(card: BoardCardResponse, structure: BoardStructurePayload): Boolean {
        val content = card.content
        if (content is ChecklistContent) {
            return content.items.isNotEmpty() && content.items.all { it.done }
        }

        return structure.cards[card.id.toString()]?.markedDone == true
    }

    /**
     * The cards this one is still waiting on.
     *
     * Dependencies on cards that have left the board are dropped rather than blocking forever: a
     * hire who dismissed the runbook card is not thereby stuck on a card nobody can see.
     */
    fun blockedBy(
        card: BoardCardResponse,
        cards: List<BoardCardResponse>,
        structure: BoardStructurePayload,
    ): List<BoardCardResponse> {
        val byId = cards.associateBy { it.id.toString() }

        return structure.cards[card.id.toString()]
            ?.dependsOn
            .orEmpty()
            .mapNotNull { byId[it.id] }
            .filterNot { isDone(it, structure) }
    }

    /**
     * The cards the hire could pick up right now, in the order their own board would offer them.
     *
     * Stage first, then the hire's own arrangement — which is why nothing here ranks anything. The
     * board's own "start with…" line is the first of these, and it is the same answer because it is
     * the same sort, not because two rules were written to agree.
     */
    fun actionable(
        cards: List<BoardCardResponse>,
        structure: BoardStructurePayload,
    ): List<BoardCardResponse> =
        cards
            .filterNot { isDone(it, structure) }
            .filter { blockedBy(it, cards, structure).isEmpty() }
            .sortedWith(compareBy({ stageOf(it, structure).ordinal }, { it.position }))

    /** A card's stage, defaulting to `NOW` — a card nobody has sorted is not put aside. */
    fun stageOf(card: BoardCardResponse, structure: BoardStructurePayload): BoardStage =
        structure.cards[card.id.toString()]?.stage ?: BoardStage.NOW

    private fun firstLine(text: String): String {
        val first = text.lineSequence().firstOrNull { it.isNotBlank() }

        return first?.trim()?.take(80) ?: "a note"
    }
}
