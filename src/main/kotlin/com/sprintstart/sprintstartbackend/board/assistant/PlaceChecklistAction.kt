package com.sprintstart.sprintstartbackend.board.assistant

import com.sprintstart.sprintstartbackend.assistant.external.BuddyActionResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.HireAction
import com.sprintstart.sprintstartbackend.assistant.external.HireActionContext
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOrder
import com.sprintstart.sprintstartbackend.assistant.external.HireActionOutcome
import com.sprintstart.sprintstartbackend.assistant.external.HireActionProposal
import com.sprintstart.sprintstartbackend.assistant.external.HireActionRequest
import com.sprintstart.sprintstartbackend.assistant.external.HireActionScope
import com.sprintstart.sprintstartbackend.board.external.HireBoardActionsApi
import com.sprintstart.sprintstartbackend.board.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.board.model.request.board.ChecklistItemRequest
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Offers to keep a list the mentor just wrote as a checklist card on the hire's board.
 *
 * **The one action whose payload is content the model wrote**, and the reason it is an action rather
 * than a board tool. Every other card is a request to show a known read; this one puts the mentor's own
 * sentences on a surface the hire treats as theirs, so the confirm button is not a formality here — it is
 * the whole safeguard. What lands is what they read in the reply above it, and the card is theirs to edit
 * or throw away the moment it exists.
 *
 * The board already had the same thing as a button under any reply holding a list (`SaveReplyToBoard`).
 * That still stands, and this is for the other half of the cases: a task whose steps the mentor had to
 * work out rather than copy, where nothing in the reply looks like a markdown list and the hire would
 * otherwise be left retyping it.
 */
@Component
@Order(HireActionOrder.PLACE_CHECKLIST)
class PlaceChecklistAction(
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "keep a checklist on your board"

    /**
     * Offers to keep the list, refusing anything that is not a list.
     *
     * A single item is refused on purpose. One line is how a model emphasises a sentence, and a
     * "checklist" of one is a card that says what the reply already said — the same reason
     * `checklistFromMarkdown` on the client will not make one either. Two is where a list starts.
     *
     * Nothing is summarised here. The lines the model passes are the lines that get kept, so what
     * lands on the board is what the hire read in the reply above the button.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val title = call.textArgument("title").take(MAX_CHECKLIST_TITLE).ifBlank { null }
        val items = call.textListArgument("items")
        if (items.size < MIN_CHECKLIST_ITEMS) {
            return refused(
                "A checklist needs at least $MIN_CHECKLIST_ITEMS items, and these were " +
                    "${items.size}. Say it in the reply instead — one line is not a list.",
            )
        }
        return offer(
            context,
            HireActionProposal(
                action = PLACE_CHECKLIST,
                label = LABEL,
                question = null,
                checklistTitle = title,
                checklistItems = items.take(MAX_CHECKLIST_ITEMS),
            ),
        )
    }

    /**
     * Keeps the proposed list as a card the hire owns.
     *
     * Re-capped here rather than trusted from the confirm: this payload is free text the client sends
     * back, and a card is cheap to write and awkward to remove.
     */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val lines = cappedLines(request.checklistItems)
        if (lines.size < MIN_CHECKLIST_ITEMS) {
            return BuddyActionResponse(ok = false, message = "There was no list left to keep.")
        }

        boardActions.addCard(
            context.board,
            ChecklistCardRequest(
                title = request.checklistTitle
                    ?.trim()
                    ?.take(MAX_CHECKLIST_TITLE)
                    ?.ifBlank { null },
                items = lines.map { ChecklistItemRequest(text = it, done = false) },
            ),
        )
        return BuddyActionResponse(
            ok = true,
            message = "Kept on your board — ${lines.size} things to tick off. It's yours now: " +
                "edit it, re-order it, throw it away.",
        )
    }

    companion object {
        const val PLACE_CHECKLIST = "place_checklist"
        const val LABEL = "Keep this as a checklist"

        val SPEC = BuddyToolSpecDto(
            name = PLACE_CHECKLIST,
            description = "Offer to keep a list you have just written as a checklist card on the " +
                "hire's board. Use it right after you have answered 'how do I start' or 'what do " +
                "I do next' with steps — the conversation is not replayed, so a list they only " +
                "read here is a list they will have to ask for again tomorrow — and whenever they ask " +
                "you to put a list on their board. " +
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
    }
}
