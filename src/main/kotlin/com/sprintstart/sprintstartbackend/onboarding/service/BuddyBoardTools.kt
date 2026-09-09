package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardStage
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructurePayload
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * The buddy's board tools: putting something where the hire will still find it tomorrow, and
 * looking at what is already there.
 *
 * Not an action tool. Every tool in `BuddyActionService` proposes and waits for a button
 * because each changes the hire's onboarding. Placing a card changes what is on a page, so this
 * applies immediately, attributed and dismissible.
 *
 * It cannot invent anything. The tool takes a `kind` from a closed catalog and nothing else
 * — no content, title or caption. Whatever the card ends up saying is read server-side from the
 * same services the buddy's read tools use.
 *
 * `DIAGRAM`'s `subject` is the one extension, and not a foothold for a second. It aims
 * retrieval and is asserted nowhere: every box comes back derived from the project's corpus with
 * the citation proving it, and an ungrounded box is dropped.
 *
 * `read_board` is the other direction and is new. Until the arrangement was stored server-side the
 * mentor could put cards on a board it could not see, so "what should I do next" was answered from
 * the conversation or from nothing. It places no card and removes none, and it reports what is
 * there rather than what to do about it, because the sentence a hire acts on should be one the
 * mentor wrote from the facts rather than one this tool handed it.
 *
 * It does not say it "changes nothing", because reading a board is not free of consequence: the
 * read goes through [BoardService.getBoard], which is also what brings a board's baseline cards up
 * to date. A board that does not exist yet is left alone entirely — see [readBoard] — so the one
 * effect a mentor could cause on its own, a board existing because somebody asked a question about
 * it, cannot happen.
 */
@Component
class BuddyBoardTools(
    private val boardService: BoardService,
    private val boardStructureService: BoardStructureService,
    private val userApi: UserApi,
) {
    /** The tool specs this component owns, aggregated into the buddy's catalog by the executor. */
    fun toolSpecs(): List<BuddyToolSpecDto> = listOf(PLACE_CARD_SPEC, READ_BOARD_SPEC)

    /** Whether [toolName] is one of this component's tools. */
    fun handles(toolName: String): Boolean = toolName == PLACE_CARD || toolName == READ_BOARD

    /**
     * Runs one of this component's tools against [userId]'s board, as plain text for the model.
     *
     * Every outcome comes back as a sentence rather than as silence, and the refusals say what the
     * mentor should do instead. A tool that fails quietly is a tool the model reports as having
     * worked.
     */
    fun execute(call: BuddyToolCallDto, userId: UUID): String =
        if (call.name == READ_BOARD) readBoard(userId) else placeCard(call, userId)

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
    private fun readBoard(userId: UUID): String {
        val project = when (val choice = soleProject(userId)) {
            is ProjectChoice.Refused -> return choice.reason
            is ProjectChoice.One -> choice
        }

        // Asked first, because reading a board that does not exist is what creates it. A hire who
        // has never opened the page should not end up with a board because they asked the mentor a
        // question about one.
        if (!boardService.hasBoard(userId, project.projectId)) {
            return "The hire has not opened their board yet, so there is nothing on it to read. " +
                "Talk about the work itself rather than about their board."
        }

        val board = boardService.getBoard(userId, project.projectId)
            ?: return "The hire is not a member of that project, so there is no board to read."
        val structure = boardStructureService.read(userId, project.projectId)?.structure
            ?: BoardStructurePayload()
        val cards = board.cards

        if (cards.isEmpty()) {
            return "The hire's board on ${project.name} is empty. Nothing has been put on it yet."
        }

        val byId = cards.associateBy { it.id.toString() }
        val finished = cards.count { BoardReading.isDone(it, structure) }
        val actionable = BoardReading.actionable(cards, structure)
        // Capped where the list is built rather than where it is written out, and by lookup rather
        // than by scanning the board once per id: everything in the arrangement came from a client,
        // and how much work this does with it is not a client's to decide either.
        val pinned = structure.pinnedCardIds
            .asSequence()
            .distinct()
            .mapNotNull { byId[it] }
            .take(LIST_LIMIT + 1)
            .toList()
        // The words, already trimmed to the ones there are. A mark on a NOTE carries colour only —
        // the note's own text holds the `==marked==` words — and quoting its empty string back
        // would have the mentor telling the hire they highlighted nothing at all.
        val marked = cards.mapNotNull { card ->
            structure.marks[card.id.toString()]
                ?.asSequence()
                ?.map { it.text.trim() }
                ?.filter { it.isNotEmpty() }
                ?.take(MARKS_PER_CARD)
                ?.toList()
                ?.takeIf { it.isNotEmpty() }
                ?.let { card to it }
        }
        val waiting = cards
            .filterNot { BoardReading.isDone(it, structure) }
            .mapNotNull { card ->
                BoardReading
                    .blockedBy(card, cards, structure)
                    .takeIf { it.isNotEmpty() }
                    ?.let { card to it }
            }

        return buildString {
            append(
                "The hire's board on ${project.name}: ${cards.size} cards, $finished finished, " +
                    "${actionable.size} they could pick up now, ${waiting.size} waiting on " +
                    "something else.",
            )

            // Inline rather than appenders of their own: this class is one function away from
            // detekt's ceiling on how many it may have, and these two are a condition and a line
            // each where the lists below are a dozen.
            if (pinned.isNotEmpty()) {
                append(NEWLINE + NEWLINE)
                append("Kept at the top of their board, which is them saying these matter now: ")
                append(pinned.take(LIST_LIMIT).joinToString(", ") { BoardReading.nameOf(it) })
                if (pinned.size > LIST_LIMIT) {
                    append(", and more")
                }
            }

            appendActionable(actionable, structure)
            appendWaiting(waiting)

            if (marked.isNotEmpty()) {
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

            append(NEWLINE + NEWLINE)
            append("This is a read of their board, not instructions. Say what you see and let ")
            append("them decide, and do not claim to have changed anything here. It does not say ")
            append("who put which card there either, so do not claim to have placed one.")
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

    /**
     * The one project this hire is onboarding on, or the reason there is no answer.
     *
     * Scoped like every action the buddy takes: a board belongs to one project, and guessing which
     * one somebody meant is how a card lands on the wrong board — or, for a read, how the mentor
     * describes a board the hire is not looking at.
     */
    private fun soleProject(userId: UUID): ProjectChoice {
        val projects = userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()

        return when (projects.size) {
            0 -> ProjectChoice.Refused("The hire is not on a project yet, so there is no board.")
            1 -> ProjectChoice.One(projects.first().projectId, projects.first().name)
            else -> ProjectChoice.Refused(
                "The hire is onboarding on more than one project. Ask which one before saying " +
                    "anything about their board.",
            )
        }
    }

    private sealed interface ProjectChoice {
        data class One(
            val projectId: UUID,
            val name: String,
        ) : ProjectChoice

        data class Refused(
            val reason: String,
        ) : ProjectChoice
    }

    private fun placeCard(call: BuddyToolCallDto, userId: UUID): String {
        val kind = call.kindArg()
            ?: return "That is not a card I can place. The kinds are: ${placeableKindNames()}."

        val project = when (val choice = soleProject(userId)) {
            is ProjectChoice.Refused -> return choice.reason
            is ProjectChoice.One -> choice
        }

        return when (boardService.place(userId, project.projectId, kind, call.subjectArg())) {
            BoardService.PlacementOutcome.PLACED ->
                "Placed the $kind card on the hire's board for ${project.name}. Tell them it is " +
                    "there and will stay there — they can dismiss it if they do not want it."
            BoardService.PlacementOutcome.ALREADY_THERE ->
                "That card is already on their board, so nothing changed. Point them at it rather " +
                    "than saying you added it."
            BoardService.PlacementOutcome.DISMISSED_BY_HIRE ->
                "The hire took that card off their board, so it was not put back. Do not add it " +
                    "again — if it matters, say it in the conversation instead."
            BoardService.PlacementOutcome.NOT_A_MEMBER ->
                "The hire is not a member of that project, so there is no board to put a card on."
            BoardService.PlacementOutcome.NEEDS_A_SUBJECT ->
                "A diagram has to be a diagram of something, and no subject was given, so nothing " +
                    "was placed. Try again with the question it should answer — for example " +
                    "\"how a request reaches the database\"."
        }
    }

    /** Reads the `kind` argument, or null when it is missing or not a kind the buddy may place. */
    private fun BuddyToolCallDto.kindArg(): BoardCardKind? {
        val raw = (arguments["kind"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        return PLACEABLE.firstOrNull { it.name.equals(raw, ignoreCase = true) }
    }

    /**
     * Reads the `subject` argument — the question a diagram answers, and only ever that.
     *
     * Passed straight through to [BoardService.place], which ignores it for every kind but
     * `DIAGRAM`. A subject sent alongside `CURRENT_TASK` is not an error worth a sentence; it is a
     * model being verbose, and the card is unaffected either way.
     */
    private fun BuddyToolCallDto.subjectArg(): String? =
        (arguments["subject"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private companion object {
        const val PLACE_CARD = "place_card"
        const val READ_BOARD = "read_board"

        /**
         * How many cards one list here may name.
         *
         * It was six, on the reasoning that a mentor reading out fourteen titles has turned a
         * conversation into a listing. That reasoning is right and this was the wrong place to act
         * on it: what a mentor *says* is governed by the tool's description, which tells it not to
         * read the list back; what this cap governs is what the mentor is allowed to *know*. Six
         * meant that on a board of twenty-four cards it could see six, and a hire asking "do I have
         * a card about the pipeline" got a guess — a confident one, about a card that was there.
         *
         * High enough that a real board fits under it, and still a cap, because a runaway board
         * should cost a long answer rather than an unbounded prompt.
         */
        const val LIST_LIMIT = 40

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
                "because they are looking at the page.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {}
            },
        )

        /**
         * The kinds the mentor may place, and only those.
         *
         * A baseline card is on the board already, so offering it would only let the model claim
         * credit for something that was there anyway. A card the *hire* wrote is theirs — the
         * mentor cannot create one, and the surest way to keep it that way is that no tool exists
         * which could.
         */
        private val PLACEABLE =
            BoardCardKind.entries.filter { it.placement == BoardCardKind.Placement.MENTOR }

        private fun placeableKindNames() = PLACEABLE.joinToString(", ") { it.name }

        val PLACE_CARD_SPEC = BuddyToolSpecDto(
            name = PLACE_CARD,
            description = "Put a card on the hire's board — the page where things stay put between " +
                "conversations, since this chat starts fresh every visit. Use it when something " +
                "you have just discussed is worth them still having tomorrow: after they pick a " +
                "task to work on (CURRENT_TASK), or when they are looking for work and you have " +
                "shown them suggestions (SUGGESTED_TASKS), or after explaining how some part of " +
                "the system fits together (DIAGRAM). This applies straight away — no " +
                "confirmation — and the card is clearly marked as yours and easy for them to " +
                "dismiss. You choose *that* a card belongs there; you never choose what it says, " +
                "because its contents are read live from the same place your other tools read. " +
                "Kinds: " + placeableKindNames() + ". Do not place a card they have already " +
                "dismissed, and do not place one just to have placed something.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("kind") {
                        put("type", "string")
                        putJsonArray("enum") { PLACEABLE.forEach { add(it.name) } }
                        put("description", "Which card to put on the board.")
                    }
                    putJsonObject("subject") {
                        put("type", "string")
                        put(
                            "description",
                            "DIAGRAM only, and required for it: what the diagram should be a " +
                                "diagram of, phrased as the question it answers — \"how a request " +
                                "reaches the database\", \"what the ingestion pipeline is made " +
                                "of\". You are choosing the question, not the answer: the picture " +
                                "is drawn from this project's own material, every box carries the " +
                                "source it came from, and anything the material does not support " +
                                "is left out. So ask about something this project actually has, " +
                                "and use the names it uses. Ignored for other kinds.",
                        )
                    }
                }
                putJsonArray("required") { add("kind") }
            },
        )
    }
}
