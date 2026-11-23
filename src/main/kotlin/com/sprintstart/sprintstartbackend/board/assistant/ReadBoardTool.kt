package com.sprintstart.sprintstartbackend.board.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireTool
import com.sprintstart.sprintstartbackend.assistant.external.HireToolContext
import com.sprintstart.sprintstartbackend.assistant.external.HireToolOrder
import com.sprintstart.sprintstartbackend.board.external.BoardReading
import com.sprintstart.sprintstartbackend.board.external.HireBoardApi
import com.sprintstart.sprintstartbackend.board.external.enums.BoardStage
import com.sprintstart.sprintstartbackend.board.external.model.BoardStructurePayload
import com.sprintstart.sprintstartbackend.board.external.model.HireBoard
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Looks at what is already on the hire's board, for the Buddy.
 *
 * Until the arrangement was stored server-side the mentor could put cards on a board it could not see,
 * so "what should I do next" was answered from the conversation or from nothing. It places no card and
 * removes none, and it reports what is there rather than what to do about it, because the sentence a
 * hire acts on should be one the mentor wrote from the facts rather than one this tool handed it.
 *
 * It does not say it "changes nothing", because reading a board is not free of consequence: the
 * read goes through [HireBoardApi.boardOf], which is also what brings a board's baseline cards up
 * to date. A board that does not exist yet is left alone entirely — see [readBoard] — so the one
 * effect a mentor could cause on its own, a board existing because somebody asked a question about
 * it, cannot happen.
 */
@Component
@Order(HireToolOrder.READ_BOARD)
class ReadBoardTool(
    private val hireBoard: HireBoardApi,
    private val userApi: UserApi,
) : HireTool {
    override val spec = READ_BOARD_SPEC

    override fun execute(call: BuddyToolCallDto, context: HireToolContext): String = readBoard(context.hire)

    /**
     * What is on the hire's board right now, as sentences.
     *
     * Facts and no verdict. The cards they can pick up come back in the order their own board offers
     * them — stage, then the arrangement they made — so "the first of these" is the same answer the
     * board's own line gives, without a second rule written to agree with the first.
     *
     * Everything is capped. A mentor that reads out fourteen card titles has turned a conversation
     * into a listing, and the hire already has the listing: it is the page they are looking at.
     */
    private fun readBoard(userId: UserId): String {
        val project = when (val choice = userApi.soleProjectOf(userId)) {
            is ProjectChoice.Refused -> return choice.reason
            is ProjectChoice.One -> choice
        }

        // Asked first, because reading a board that does not exist is what creates it. A hire who
        // has never opened the page should not end up with a board because they asked the mentor a
        // question about one.
        val whose = HireBoard(userId, project.projectId).takeIf(hireBoard::hasBoard)
            ?: return "The hire has not opened their board yet, so there is nothing on it to read. " +
                "Talk about the work itself rather than about their board."

        val board = hireBoard.boardOf(whose)
            ?: return "The hire is not a member of that project, so there is no board to read."
        val structure = hireBoard.structureOf(whose)?.structure ?: BoardStructurePayload()
        val cards = board.cards

        if (cards.isEmpty()) {
            return "The hire's board on ${project.name} is empty. Nothing has been put on it yet."
        }

        return describe(project.name, cards, structure)
    }

    private fun describe(name: String, cards: List<BoardCardResponse>, structure: BoardStructurePayload): String {
        val finished = cards.count { BoardReading.isDone(it, structure) }
        val actionable = BoardReading.actionable(cards, structure)
        val pinned = pinnedCards(cards, structure)
        val waiting = waitingCards(cards, structure)

        return buildString {
            append(
                "The hire's board on $name: ${cards.size} cards, $finished finished, " +
                    "${actionable.size} they could pick up now, ${waiting.size} waiting on " +
                    "something else.",
            )
            appendPinned(pinned)
            appendActionable(actionable, structure)
            appendWaiting(waiting)
            appendMarked(markedWords(cards, structure))
            append(BoardReading.cardsSection(cards, LIST_LIMIT))

            append(NEWLINE + NEWLINE)
            append("This is a read of their board, not instructions. Say what you see and let ")
            append("them decide, and do not claim to have changed anything here. It does not say ")
            append("who put which card there either, so do not claim to have placed one.")
        }
    }

    /**
     * The cards the hire pinned, in their order.
     *
     * Capped where the list is built rather than where it is written out, and by lookup rather than by
     * scanning the board once per id: everything in the arrangement came from a client, and how much
     * work this does with it is not a client's to decide either.
     */
    private fun pinnedCards(cards: List<BoardCardResponse>, structure: BoardStructurePayload): List<BoardCardResponse> {
        val byId = cards.associateBy { it.id.toString() }
        return structure.pinnedCardIds
            .asSequence()
            .distinct()
            .mapNotNull { byId[it] }
            .take(LIST_LIMIT + 1)
            .toList()
    }

    private fun waitingCards(
        cards: List<BoardCardResponse>,
        structure: BoardStructurePayload,
    ): List<Pair<BoardCardResponse, List<BoardCardResponse>>> =
        cards
            .filterNot { BoardReading.isDone(it, structure) }
            .mapNotNull { card ->
                BoardReading
                    .blockedBy(card, cards, structure)
                    .takeIf { it.isNotEmpty() }
                    ?.let { card to it }
            }

    private fun StringBuilder.appendPinned(pinned: List<BoardCardResponse>) {
        if (pinned.isEmpty()) return
        append(NEWLINE + NEWLINE)
        append("Kept at the top of their board, which is them saying these matter now: ")
        append(pinned.take(LIST_LIMIT).joinToString(", ") { BoardReading.nameOf(it) })
        if (pinned.size > LIST_LIMIT) append(", and more")
    }

    /**
     * The words the hire highlighted on each card, already trimmed to the ones there are.
     *
     * A mark on a NOTE carries colour only -- the note's own text holds the `==marked==` words -- and
     * quoting its empty string back would have the mentor telling the hire they highlighted nothing
     * at all.
     */
    private fun markedWords(
        cards: List<BoardCardResponse>,
        structure: BoardStructurePayload,
    ): List<Pair<BoardCardResponse, List<String>>> =
        cards.mapNotNull { card ->
            structure.marks[card.id.toString()]
                ?.asSequence()
                ?.map { it.text.trim() }
                ?.filter { it.isNotEmpty() }
                ?.take(MARKS_PER_CARD)
                ?.toList()
                ?.takeIf { it.isNotEmpty() }
                ?.let { card to it }
        }

    private fun StringBuilder.appendMarked(marked: List<Pair<BoardCardResponse, List<String>>>) {
        if (marked.isEmpty()) return
        append(NEWLINE + NEWLINE)
        append("Highlighted — the hire saying which part of a card mattered. Ask about the ")
        append("part rather than the whole card:")
        marked.take(LIST_LIMIT).forEach { (card, marks) ->
            val words = marks.joinToString("; ") { mark ->
                if (mark.length > QUOTE_LIMIT) mark.take(QUOTE_LIMIT) + "…" else mark
            }
            append(NEWLINE + "- " + BoardReading.nameOf(card) + ": \"" + words + "\"")
        }
    }

    /**
     * The cards they could pick up, in their own board's order.
     *
     * Its own function, and the one below it too — a split detekt asked for and which the sentences
     * were already making anyway: the opening line counts things, and these name them. Each writes
     * nothing at all when it has nothing to name, because a heading over an empty list is the mentor
     * telling the hire about something that is not there.
     */
    private fun StringBuilder.appendActionable(
        actionable: List<BoardCardResponse>,
        structure: BoardStructurePayload,
    ) {
        if (actionable.isEmpty()) return

        append(NEWLINE + NEWLINE)
        append("They can pick up, in the order their own board offers them. The board's ")
        append("own start-with line names the first of these:")
        actionable.take(LIST_LIMIT).forEach { card ->
            append(NEWLINE + "- " + BoardReading.nameOf(card))
            if (BoardReading.stageOf(card, structure) == BoardStage.LATER) {
                append(" (they put this one aside for later)")
            }
        }
        if (actionable.size > LIST_LIMIT) {
            append(NEWLINE + "- and ${actionable.size - LIST_LIMIT} more")
        }
    }

    /** The cards that cannot be started yet, each with what it is waiting on. */
    private fun StringBuilder.appendWaiting(
        waiting: List<Pair<BoardCardResponse, List<BoardCardResponse>>>,
    ) {
        if (waiting.isEmpty()) return

        append(NEWLINE + NEWLINE + "Waiting on something first:")
        waiting.take(LIST_LIMIT).forEach { (card, blockers) ->
            val on = blockers.joinToString(", ") { BoardReading.nameOf(it) }
            append(NEWLINE + "- " + BoardReading.nameOf(card) + " — waits on " + on)
        }
        if (waiting.size > LIST_LIMIT) {
            append(NEWLINE + "- and ${waiting.size - LIST_LIMIT} more")
        }
    }

    companion object {
        const val READ_BOARD = "read_board"

        /** How much of one highlight is quoted before it is cut. Enough for a sentence. */
        const val QUOTE_LIMIT = 120

        /**
         * How many highlights on one card are quoted.
         *
         * [LIST_LIMIT] caps how many cards are named, which left one card free to carry as many
         * marks as a client cared to store and put every one of them in the prompt. Six is what a
         * mentor can say something about; the rest are on the page in front of the hire.
         */
        const val MARKS_PER_CARD = 6

        /** Written out, so that no editing step has to survive an escape sequence intact. */
        const val NEWLINE = "\n"

        val READ_BOARD_SPEC = BuddyToolSpecDto(
            name = READ_BOARD,
            description = "Look at the hire's board — the page where their work sits between " +
                "conversations. Use it when they ask what to do next, when they say they are " +
                "stuck or do not know where to start, and before suggesting anything, so that " +
                "what you suggest is about the board they actually have rather than the one you " +
                "imagine. It tells you which cards they could pick up now, in the order their " +
                "own board offers them, which ones are waiting on something and on what, and on " +
                "how many of them they have highlighted something. A highlight is them saying " +
                "which part mattered, so ask about that part rather than about the whole card. " +
                "It only looks: it puts no card on their board and takes none off, so do not use " +
                "it to claim you have done something, and do not read the list back to them, " +
                "because they are looking at the page. " +
                "Look even when you already know the answer. A hire saying they have finished part " +
                "of something, or asking what comes next, is talking about a card that is in front " +
                "of them — and the conversation is not the board: what you remember telling them is " +
                "not proof of what they kept. This is also the only place card ids come from, and it " +
                "shows what their own notes, links and checklists say — so editing, tidying, " +
                "clearing or rearranging anything on their board starts here.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            },
        )
    }
}
