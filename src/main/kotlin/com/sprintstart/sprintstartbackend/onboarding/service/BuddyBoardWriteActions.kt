package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyActionType
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistItemRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyActionResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyActionService.BuddyActionProposal
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyActionService.ProposeOutcome
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * The actions that put the mentor's own words on a hire's board.
 *
 * Split out of [BuddyActionService] once there were four of them, for the reason [BuddyBoardTools]
 * is split out of [BuddyToolExecutor]: the rest of that class wraps existing `/me/...` operations
 * one for one, and these five do something different enough to be worth reading on their own.
 *
 * **What they have in common is the guarantee, not the card kind.** Each one only ever *proposes*:
 * the tool call writes nothing, the hire sees a button with the content on it, and the confirm is
 * what reaches the board. That gate is what lets the mentor author content at all — the
 * no-confirm `place_card` tool stays limited to live reads whose contents it does not choose (see
 * [BuddyBoardTools]). Widening that tool would be the hole; these are not one.
 *
 * The payload travels with the proposal rather than being worked out again at confirm time, which
 * matters more here than anywhere else in the catalog: these payloads are *words*, not target ids,
 * so re-deriving them could keep a card whose wording the hire never saw.
 *
 * Four actions, each with a propose and a perform half, plus the argument readers they share —
 * hence the suppressed function count.
 */
@Component
@Suppress("TooManyFunctions")
class BuddyBoardWriteActions(
    private val boardService: BoardService,
) {
    /** The five tools, offered by [BuddyActionService] alongside its own. */
    fun specs(): List<BuddyToolSpecDto> =
        listOf(
            PLACE_CHECKLIST_SPEC,
            AMEND_CHECKLIST_SPEC,
            TICK_CHECKLIST_SPEC,
            REWORD_CHECKLIST_SPEC,
            PLACE_NOTE_SPEC,
        )

    /** Whether this is one of ours, so the caller's dispatch need not know the five names. */
    fun handles(type: BuddyActionType): Boolean = type in HANDLED

    /** Turns one of these tool calls into a proposal, or into the reason there is none. */
    fun propose(call: BuddyToolCallDto, type: BuddyActionType, projectName: String): ProposeOutcome =
        when (type) {
            BuddyActionType.PLACE_CHECKLIST -> proposeChecklist(call, type, projectName)
            BuddyActionType.AMEND_CHECKLIST -> proposeAmendment(call, type, projectName)
            BuddyActionType.TICK_CHECKLIST_ITEMS -> proposeTicks(call, type, projectName)
            BuddyActionType.REWORD_CHECKLIST_ITEM -> proposeReword(call, type, projectName)
            BuddyActionType.PLACE_NOTE -> proposeNote(call, type, projectName)
            else -> error("$type is not a board write; handles() keeps it out of here")
        }

    /** Runs a confirmed one. The caller has already resolved whose board, and which project. */
    fun perform(
        type: BuddyActionType,
        userId: UUID,
        projectId: UUID,
        payload: BoardWritePayload,
    ): BuddyActionResponse = when (type) {
        BuddyActionType.PLACE_CHECKLIST ->
            placeChecklist(userId, projectId, payload.checklistTitle, payload.checklistItems)
        BuddyActionType.AMEND_CHECKLIST ->
            amendChecklist(userId, projectId, payload.cardId, payload.checklistItems)
        BuddyActionType.TICK_CHECKLIST_ITEMS ->
            tickItems(userId, projectId, payload.cardId, payload.checklistItems)
        BuddyActionType.REWORD_CHECKLIST_ITEM ->
            rewordItem(userId, projectId, payload.cardId, payload.lineBefore, payload.lineAfter)
        BuddyActionType.PLACE_NOTE -> placeNote(userId, projectId, payload.noteText)
        else -> error("$type is not a board write; handles() keeps it out of here")
    }

    /** What a confirmed board write carries, unpacked from the request by the caller. */
    data class BoardWritePayload(
        val checklistTitle: String? = null,
        val checklistItems: List<String>? = null,
        val cardId: UUID? = null,
        val noteText: String? = null,
        val lineBefore: String? = null,
        val lineAfter: String? = null,
    )

    /**
     * Offers to keep a list the mentor wrote as a card, refusing anything that is not a list.
     *
     * A single item is refused on purpose. One line is how a model emphasises a sentence, and a
     * "checklist" of one is a card that says what the reply already said — the same reason
     * `checklistFromMarkdown` on the client will not make one either. Two is where a list starts.
     *
     * Nothing is summarised here. The lines the model passes are the lines that get kept, so what
     * lands on the board is what the hire read in the reply above the button.
     */
    private fun proposeChecklist(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        projectName: String,
    ): ProposeOutcome {
        val title = call
            .stringArg("title")
            .trim()
            .take(MAX_CHECKLIST_TITLE)
            .ifBlank { null }
        val items = call.stringListArg("items")
        return when {
            items.size < MIN_CHECKLIST_ITEMS ->
                ProposeOutcome(
                    "A checklist needs at least $MIN_CHECKLIST_ITEMS items, and these were " +
                        "${items.size}. Say it in the reply instead — one line is not a list.",
                    null,
                )
            else -> offer(
                type,
                projectName,
                checklistTitle = title,
                checklistItems = items.take(MAX_CHECKLIST_ITEMS),
            )
        }
    }

    /**
     * Keeps the proposed list as a card the hire owns.
     *
     * Re-capped here rather than trusted from the confirm: this is the only action whose payload is
     * free text the client sends back, and a card is cheap to write and awkward to remove.
     */
    private fun placeChecklist(
        userId: UUID,
        projectId: UUID,
        title: String?,
        items: List<String>?,
    ): BuddyActionResponse {
        val lines = items
            .orEmpty()
            .map { it.trim().take(MAX_CHECKLIST_ITEM_LENGTH) }
            .filter { it.isNotBlank() }
            .take(MAX_CHECKLIST_ITEMS)

        if (lines.size < MIN_CHECKLIST_ITEMS) {
            return BuddyActionResponse(ok = false, message = "There was no list left to keep.")
        }

        boardService.addAuthoredCard(
            userId,
            projectId,
            ChecklistCardRequest(
                title = title?.trim()?.take(MAX_CHECKLIST_TITLE)?.ifBlank { null },
                items = lines.map { ChecklistItemRequest(text = it, done = false) },
            ),
        )
        return BuddyActionResponse(
            ok = true,
            message = "Kept on your board — ${lines.size} things to tick off. It's yours now: " +
                "edit it, re-order it, throw it away.",
        )
    }

    /**
     * Offers to add lines to a checklist the hire already has.
     *
     * Takes only the *new* lines, never the whole list. The mentor is not asked to send back what
     * is already on the card, so there is no version of this where it quietly rewords or drops one
     * — see `BoardService.appendChecklistItems`, which is where that guarantee lives.
     *
     * The card id comes from `read_board`, which is the only place the mentor learns that a
     * checklist exists at all.
     */
    private fun proposeAmendment(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        projectName: String,
    ): ProposeOutcome {
        val cardId = call.uuidArg("card_id")
        val items = call.stringListArg("items")
        return when {
            cardId == null ->
                ProposeOutcome(
                    "No card_id was provided. Read read_board to find the checklist you mean, and " +
                        "pass its id.",
                    null,
                )
            items.isEmpty() ->
                ProposeOutcome("No lines were provided to add.", null)
            else -> offer(
                type,
                projectName,
                cardId = cardId,
                checklistItems = items.take(MAX_CHECKLIST_ITEMS),
            )
        }
    }

    /**
     * Offers to tick lines the hire has said they finished.
     *
     * Same shape as an amendment and the same refusals, because it is the same card and the same
     * risk of naming one that is not theirs. What differs is where the lines come from: an
     * amendment's are the mentor's, these are the hire's own — quoted back from the card so they
     * can see which ones before agreeing.
     */
    private fun proposeTicks(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        projectName: String,
    ): ProposeOutcome {
        val cardId = call.uuidArg("card_id")
        val items = call.stringListArg("items")
        return when {
            cardId == null ->
                ProposeOutcome(
                    "No card_id was provided. Read read_board to find the checklist you mean, and " +
                        "pass its id.",
                    null,
                )
            items.isEmpty() ->
                ProposeOutcome("No lines were named to tick off.", null)
            else -> offer(
                type,
                projectName,
                cardId = cardId,
                checklistItems = items.take(MAX_CHECKLIST_ITEMS),
            )
        }
    }

    /** Ticks the named lines, and can do nothing else to the card. */
    private fun tickItems(
        userId: UUID,
        projectId: UUID,
        cardId: UUID?,
        items: List<String>?,
    ): BuddyActionResponse {
        if (cardId == null) {
            return BuddyActionResponse(ok = false, message = "No card was proposed to tick off.")
        }
        val lines = items.orEmpty().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.isEmpty()) {
            return BuddyActionResponse(ok = false, message = "There was nothing left to tick off.")
        }

        return try {
            when (val ticked = boardService.tickChecklistItems(userId, projectId, cardId, lines)) {
                0 -> BuddyActionResponse(
                    ok = false,
                    // Says which of the two it was, because they need different answers: one is
                    // "you already did that", the other is "I named the wrong line".
                    message = "Nothing changed — either those lines are already ticked, or they " +
                        "are not the ones on that card.",
                )
                else -> BuddyActionResponse(
                    ok = true,
                    message = "Ticked $ticked off. Nothing else on the list changed.",
                )
            }
        } catch (ex: ResponseStatusException) {
            BuddyActionResponse(ok = false, message = ex.reason ?: "That list could not be ticked.")
        }
    }

    /**
     * Offers to rewrite one line the hire has asked to be clearer.
     *
     * Both wordings travel with the proposal, so the confirm can show the change rather than
     * assert one. Refuses a rewording that says nothing, and a rewording that says exactly what
     * the line already said.
     */
    private fun proposeReword(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        projectName: String,
    ): ProposeOutcome {
        val cardId = call.uuidArg("card_id")
        val before = call.stringArg("line").trim()
        val after = call.stringArg("reworded").trim().take(MAX_CHECKLIST_ITEM_LENGTH)
        return when {
            cardId == null ->
                ProposeOutcome(
                    "No card_id was provided. Read read_board to find the checklist you mean, and " +
                        "pass its id.",
                    null,
                )
            before.isEmpty() || after.isEmpty() ->
                ProposeOutcome("Both the line and its rewording are needed.", null)
            before.equals(after, ignoreCase = true) ->
                ProposeOutcome("That is what the line already says — there is nothing to change.", null)
            else -> offer(type, projectName, cardId = cardId, lineBefore = before, lineAfter = after)
        }
    }

    /** Rewrites the named line, and can do nothing else to the card. */
    private fun rewordItem(
        userId: UUID,
        projectId: UUID,
        cardId: UUID?,
        before: String?,
        after: String?,
    ): BuddyActionResponse {
        if (cardId == null || before.isNullOrBlank() || after.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "There was no line to reword.")
        }
        val reworded = after.take(MAX_CHECKLIST_ITEM_LENGTH)

        return try {
            if (boardService.rewordChecklistItem(userId, projectId, cardId, before, reworded)) {
                BuddyActionResponse(
                    ok = true,
                    message = "Reworded. It keeps its place and its tick; nothing else changed.",
                )
            } else {
                BuddyActionResponse(
                    ok = false,
                    message = "Nothing changed — that line is not on the card, or more than one " +
                        "line reads exactly like it.",
                )
            }
        } catch (ex: ResponseStatusException) {
            BuddyActionResponse(ok = false, message = ex.reason ?: "That line could not be reworded.")
        }
    }

    /**
     * Offers to keep an explanation as a note.
     *
     * Refuses a note that is only a heading's worth of words: every reply already carries a button
     * that keeps the whole answer, so a card holding one sentence out of it is worth less than the
     * thing the hire could have pressed anyway.
     */
    private fun proposeNote(
        call: BuddyToolCallDto,
        type: BuddyActionType,
        projectName: String,
    ): ProposeOutcome {
        val text = call.stringArg("text").trim()
        return when {
            text.length < MIN_NOTE_LENGTH ->
                ProposeOutcome(
                    "That is too short to be worth a card of its own — say it in the reply instead.",
                    null,
                )
            else -> offer(type, projectName, noteText = text.take(MAX_NOTE_LENGTH))
        }
    }

    /** Adds the proposed lines to the hire's card, and can do nothing else to it. */
    private fun amendChecklist(
        userId: UUID,
        projectId: UUID,
        cardId: UUID?,
        items: List<String>?,
    ): BuddyActionResponse {
        if (cardId == null) {
            return BuddyActionResponse(ok = false, message = "No card was proposed to add to.")
        }
        val lines = items
            .orEmpty()
            .map { it.trim().take(MAX_CHECKLIST_ITEM_LENGTH) }
            .filter { it.isNotBlank() }
            .take(MAX_CHECKLIST_ITEMS)
        if (lines.isEmpty()) {
            return BuddyActionResponse(ok = false, message = "There was nothing left to add.")
        }

        return try {
            boardService.appendChecklistItems(userId, projectId, cardId, lines)
            BuddyActionResponse(
                ok = true,
                message = "Added ${lines.size} to that list. Nothing else on it changed.",
            )
        } catch (ex: ResponseStatusException) {
            // A card that is not theirs, or not a checklist. Both are things the hire can see for
            // themselves, so they come back as the sentence rather than as a failed confirm.
            BuddyActionResponse(ok = false, message = ex.reason ?: "That list could not be added to.")
        }
    }

    private fun placeNote(userId: UUID, projectId: UUID, text: String?): BuddyActionResponse {
        val body = text?.trim()?.take(MAX_NOTE_LENGTH).orEmpty()
        if (body.length < MIN_NOTE_LENGTH) {
            return BuddyActionResponse(ok = false, message = "There was no note left to keep.")
        }

        boardService.addAuthoredCard(userId, projectId, NoteCardRequest(text = body))
        return BuddyActionResponse(ok = true, message = "Kept on your board. It's yours — edit it as you like.")
    }

    /**
     * One of these as an offer the hire can confirm.
     *
     * The tool result is the same promise every action makes — offered, not done — with the part
     * that is specific to these five spelled out: the mentor never learns what became of it, and a
     * confirmed proposal leaves the screen, so pointing at the button afterwards is how a hire ends
     * up being told they must be missing something that is not there.
     */
    @Suppress("LongParameterList") // One per payload field; a wrapper here would only hide them.
    private fun offer(
        type: BuddyActionType,
        projectName: String,
        checklistTitle: String? = null,
        checklistItems: List<String>? = null,
        cardId: UUID? = null,
        noteText: String? = null,
        lineBefore: String? = null,
        lineAfter: String? = null,
    ): ProposeOutcome =
        ProposeOutcome(
            toolResult = "Proposed to the hire on $projectName: \u201C${type.label}\u201D. They will see a " +
                "confirm button showing what would be kept; it runs only if they click. Offer it " +
                "\u2014 do not claim it is done. You will never be told whether they confirmed it, and a " +
                "confirmed proposal leaves the screen, so do not describe the button or ask them to " +
                "click it again. If they say nothing happened, believe them and call the tool afresh.",
            proposal = BuddyActionProposal(
                action = type.toolName,
                label = type.label,
                question = null,
                checklistTitle = checklistTitle,
                checklistItems = checklistItems,
                cardId = cardId,
                noteText = noteText,
                lineBefore = lineBefore,
                lineAfter = lineAfter,
            ),
        )

    /** A list-of-strings argument, with anything that is not a usable line dropped. */
    private fun BuddyToolCallDto.stringListArg(name: String): List<String> =
        (arguments[name] as? JsonArray)
            .orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { line -> line.isNotBlank() } }

    private fun BuddyToolCallDto.stringArg(name: String): String =
        (arguments[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

    private fun BuddyToolCallDto.uuidArg(name: String): UUID? =
        runCatching { UUID.fromString(stringArg(name)) }.getOrNull()

    private companion object {
        val HANDLED = setOf(
            BuddyActionType.PLACE_CHECKLIST,
            BuddyActionType.AMEND_CHECKLIST,
            BuddyActionType.PLACE_NOTE,
            BuddyActionType.TICK_CHECKLIST_ITEMS,
            BuddyActionType.REWORD_CHECKLIST_ITEM,
        )

        /**
         * The fewest lines that count as a list worth keeping.
         *
         * Two, matching `checklistFromMarkdown` on the client, and for the same reason: one bullet
         * is how a model emphasises a sentence, and a card made of it repeats the reply above it.
         */
        const val MIN_CHECKLIST_ITEMS = 2

        /** How many lines a card will take. Past this it is a document, not a checklist. */
        const val MAX_CHECKLIST_ITEMS = 25

        /** A step is a line, not a paragraph — anything longer is cut rather than refused. */
        const val MAX_CHECKLIST_ITEM_LENGTH = 300

        /** A heading length: long enough to say what the list is, short enough not to wrap. */
        const val MAX_CHECKLIST_TITLE = 120

        /**
         * The fewest characters worth a note card of its own.
         *
         * Every reply already carries a button that keeps the whole answer, so a card holding one
         * short sentence out of it is worth less than the thing the hire could have pressed anyway.
         */
        const val MIN_NOTE_LENGTH = 80

        /** Past this a note is a document. `NoteCard` already folds a long one behind a count. */
        const val MAX_NOTE_LENGTH = 2000

        val PLACE_CHECKLIST_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.PLACE_CHECKLIST.toolName,
            description = "Offer to keep a list you have just written as a checklist card on the " +
                "hire's board. Use it right after you have answered 'how do I start' or 'what do " +
                "I do next' with steps — the conversation is not replayed, so a list they only " +
                "read here is a list they will have to ask for again tomorrow. " +
                "ONE ITEM PER THING THEY DO, not one per line you wrote. A card is a flat list of " +
                "things to tick off, so an answer with headed sections and bullets under them " +
                "becomes one item per section, with the detail folded into that item's own words " +
                "— 'Set up locally: npm install and npm run dev in the frontend, backend per the " +
                "repo docs', not a separate item for each bullet and another for the heading. " +
                "Aim for 3-7 items. Write each one as something they can finish and tick: start " +
                "with a verb, keep it to a line, and drop anything that is context rather than a " +
                "step (a goal, a list of dependencies, an offer to help further). Say the same " +
                "things you said in the reply — this puts your sentences on a surface the hire " +
                "treats as their own, so nothing may appear on the card that they have not just " +
                "read. Never use it for a list the task itself already states: those are the " +
                "task's words and the board already offers them. " +
                "This does NOT write anything by itself; the hire sees a confirm button with the " +
                "lines on it and only they can keep it. Ticking it changes nothing anywhere else " +
                "— it is their working copy, not a status.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("title") {
                        put("type", "string")
                        put("description", "What the list is about, in a few words. The card's heading.")
                    }
                    putJsonObject("items") {
                        put("type", "array")
                        put(
                            "description",
                            "One entry per thing the hire does, in the order they do them, in the " +
                                "words you used in the reply. Fold a section's bullets into that " +
                                "section's entry rather than making an entry of each. Aim for " +
                                "3-7; at least $MIN_CHECKLIST_ITEMS, and anything past " +
                                "$MAX_CHECKLIST_ITEMS is dropped.",
                        )
                        putJsonObject("items") { put("type", "string") }
                    }
                }
                putJsonArray("required") {
                    add("title")
                    add("items")
                }
            },
        )

        val AMEND_CHECKLIST_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.AMEND_CHECKLIST.toolName,
            description = "Offer to add steps to a checklist the hire ALREADY has, instead of " +
                "making a second card beside it. WHENEVER you answer what comes next, check " +
                "whether it belongs on a list they already have — it usually does. 'I have done " +
                "the first two, what now?' is this tool, every time: answering in prose alone " +
                "leaves them holding something that is gone by tomorrow, beside a card that still " +
                "says seven things and gives no hint which. Having the list in this conversation " +
                "is not the same as it being on their board, and is never a reason to skip the " +
                "board — read read_board first and pass that card's id. " +
                "Pass ONLY the new lines — never the ones already on the card. You " +
                "cannot reword, re-order or remove what is there, and you should not try: those " +
                "lines are the hire's, even the ones you suggested, and the card is what they are " +
                "working from. This does NOT write anything by itself; they see a confirm button " +
                "showing only what would be added.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("card_id") {
                        put("type", "string")
                        put("description", "The checklist card's id, exactly as read_board gave it.")
                    }
                    putJsonObject("items") {
                        put("type", "array")
                        put("description", "Only the new lines, in the order they should be done.")
                        putJsonObject("items") { put("type", "string") }
                    }
                }
                putJsonArray("required") {
                    add("card_id")
                    add("items")
                }
            },
        )

        val TICK_CHECKLIST_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.TICK_CHECKLIST_ITEMS.toolName,
            description = "Offer to tick lines off a checklist of theirs, when the hire SAYS they " +
                "have done them. 'I have done the first two' is this tool. Read read_board for the " +
                "card's id and the exact lines, and pass the lines back word for word — they are " +
                "matched by their words, so a paraphrase ticks nothing. " +
                "Only ever when they tell you. Never conclude from the conversation that a step " +
                "looks done, never tick something as a side effect of answering, and never tick " +
                "the last line to tidy a list up: a board that ticks itself because a model read " +
                "something into a sentence is a board whose state nobody can trust. It cannot " +
                "un-tick, so if they say they were wrong, tell them the checkbox on the card is " +
                "theirs to click. This does NOT change anything by itself; they see a confirm " +
                "button naming the lines.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("card_id") {
                        put("type", "string")
                        put("description", "The checklist card's id, exactly as read_board gave it.")
                    }
                    putJsonObject("items") {
                        put("type", "array")
                        put(
                            "description",
                            "The lines to tick, word for word as read_board shows them on the card.",
                        )
                        putJsonObject("items") { put("type", "string") }
                    }
                }
                putJsonArray("required") {
                    add("card_id")
                    add("items")
                }
            },
        )

        val REWORD_CHECKLIST_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.REWORD_CHECKLIST_ITEM.toolName,
            description = "Offer to rewrite ONE line of a checklist, when the hire asks for that " +
                "line to be clearer or says it no longer says the right thing. Only when they ask " +
                "about a line: their words are theirs, including the ones you suggested, and " +
                "tidying a list nobody asked you to tidy is how a board stops being somebody's " +
                "own. Read read_board for the card's id and the line word for word — it is matched " +
                "by its words, and a line two of them read alike is refused rather than guessed " +
                "at. The line keeps its place and its tick: rewording a step is not undoing it, so " +
                "do not use this to mark something done. For a step that is missing use " +
                "amend_checklist; this replaces, it does not add. They see both wordings on the " +
                "confirm button and only they can apply it.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("card_id") {
                        put("type", "string")
                        put("description", "The checklist card's id, exactly as read_board gave it.")
                    }
                    putJsonObject("line") {
                        put("type", "string")
                        put("description", "The line as it reads now, word for word from read_board.")
                    }
                    putJsonObject("reworded") {
                        put("type", "string")
                        put("description", "What it should say instead. One line, still a thing to tick off.")
                    }
                }
                putJsonArray("required") {
                    add("card_id")
                    add("line")
                    add("reworded")
                }
            },
        )

        val PLACE_NOTE_SPEC = BuddyToolSpecDto(
            name = BuddyActionType.PLACE_NOTE.toolName,
            description = "Offer to keep an explanation you have just given as a note on the " +
                "hire's board. Use it sparingly and only for something that will still be true " +
                "and still be needed next week — how a part of this system works, a convention " +
                "the team holds to. Not for an answer about right now, and not for steps: those " +
                "are place_checklist. Pass the explanation in your own words from the reply, " +
                "shortened to what is worth keeping. Every reply already carries a button that " +
                "keeps the whole answer, so only offer this when a card is better than that. This " +
                "does NOT write anything by itself; the hire sees a confirm button with the text " +
                "on it, and the note is theirs to edit afterwards.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("text") {
                        put("type", "string")
                        put(
                            "description",
                            "The note, in your words from the reply. Markdown; first line is the heading.",
                        )
                    }
                }
                putJsonArray("required") { add("text") }
            },
        )
    }
}
