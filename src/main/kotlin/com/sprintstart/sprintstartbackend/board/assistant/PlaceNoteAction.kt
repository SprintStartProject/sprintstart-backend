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
import com.sprintstart.sprintstartbackend.board.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Offers to keep an explanation the mentor just gave as a note.
 *
 * The weakest of the board writes on its own — every reply already carries a "keep this answer" button
 * that needs no tool. What this adds is the mentor *offering* when it can tell the answer is worth having
 * tomorrow, rather than waiting to be asked.
 */
@Component
@Order(HireActionOrder.PLACE_NOTE)
class PlaceNoteAction(
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "keep a note on your board"

    /**
     * Refuses a note that is only a heading's worth of words: a card holding one sentence out of a reply
     * is worth less than the button that keeps the whole answer.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val text = call.textArgument("text")
        if (text.length < MIN_NOTE_LENGTH) {
            return refused("That is too short to be worth a card of its own — say it in the reply instead.")
        }
        return offer(
            context,
            HireActionProposal(
                action = PLACE_NOTE,
                label = LABEL,
                question = null,
                noteText = text.take(MAX_NOTE_LENGTH),
            ),
        )
    }

    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val body = request.noteText
            ?.trim()
            ?.take(MAX_NOTE_LENGTH)
            .orEmpty()
        if (body.length < MIN_NOTE_LENGTH) {
            return BuddyActionResponse(ok = false, message = "There was no note left to keep.")
        }

        boardActions.addCard(context.board, NoteCardRequest(text = body))
        return BuddyActionResponse(ok = true, message = "Kept on your board. It's yours — edit it as you like.")
    }

    companion object {
        const val PLACE_NOTE = "place_note"
        const val LABEL = "Keep this as a note"

        val SPEC = BuddyToolSpecDto(
            name = PLACE_NOTE,
            description = "Offer to keep an explanation you have just given as a note on the " +
                "hire's board. Use it sparingly and only for something that will still be true " +
                "and still be needed next week — how a part of this system works, a convention " +
                "the team holds to — or whenever the hire asks you to keep something as a note; their " +
                "asking outweighs your sense of what is worth keeping. Not for steps: those " +
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
