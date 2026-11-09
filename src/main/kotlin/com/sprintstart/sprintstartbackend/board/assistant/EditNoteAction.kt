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
import com.sprintstart.sprintstartbackend.board.external.BoardCardVersion
import com.sprintstart.sprintstartbackend.board.external.BoardReading
import com.sprintstart.sprintstartbackend.board.external.HireBoardActionsApi
import com.sprintstart.sprintstartbackend.board.external.HireBoardApi
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.board.model.response.board.NoteContent
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Offers to rewrite one of the hire's notes, when they ask for it to be changed.
 *
 * The first of the edits that reach past checklists into anything the hire wrote. What makes that
 * acceptable is the pair every buddy board write carries: the confirm shows the new words before anything
 * is written, and the card says afterwards that the buddy changed it (`BoardCard.recordChange`).
 */
@Component
@Order(HireActionOrder.EDIT_NOTE)
class EditNoteAction(
    private val hireBoard: HireBoardApi,
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "update a note on your board"

    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val text = call.textArgument("text").take(MAX_NOTE_LENGTH)
        val card = when (val found = hireBoard.ownCard(call, context.board, BoardCardKind.NOTE)) {
            is Found.Refused -> return refused(found.reason)
            is Found.Ok -> found.value
        }
        if (text.isEmpty()) return refused("No new text was given for the note.")
        // read_board shows only the start of a long note. A rewrite of the part the mentor saw would
        // drop the part it did not, and the hire would confirm a loss they could not see on the offer.
        if ((card.content as? NoteContent)?.text.orEmpty().length > BoardReading.NOTE_PREVIEW) {
            return refused(
                "That note is longer than read_board shows you, so a rewrite would lose the part you " +
                    "have not seen. Tell the hire what to change and let them edit it themselves.",
            )
        }

        return offer(
            context,
            HireActionProposal(
                action = EDIT_NOTE,
                label = LABEL,
                question = null,
                cardId = card.id,
                noteText = text,
                basedOn = BoardCardVersion.of(card.content),
                preview = "Rewrite your note “${BoardReading.nameOf(card)}” so it reads as shown.",
            ),
        )
    }

    /**
     * Writes the confirmed text, unless the note's words have moved on since the proposal read them.
     *
     * An edit replaces the card whole, and what the confirm showed was worked out against the card as it
     * was then. `basedOn` is the fingerprint of that card; `HireBoardActionsApi.editCard` compares it
     * against the row it holds the lock on, because a check made here would leave a gap before that lock
     * is taken. A confirm that carries no fingerprint is refused the same way.
     */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val cardId = request.cardId
        val text = request.noteText
            ?.trim()
            ?.take(MAX_NOTE_LENGTH)
            .orEmpty()
        if (cardId == null || text.isEmpty()) {
            return BuddyActionResponse(ok = false, message = "There was no note change to make.")
        }
        boardActions.editCard(context.board, BoardCardId(cardId), NoteCardRequest(text = text), request.basedOn)
        return EDITED
    }

    companion object {
        const val EDIT_NOTE = "edit_note"
        const val LABEL = "Update this note"

        val SPEC = BuddyToolSpecDto(
            name = EDIT_NOTE,
            description = "Offer to rewrite one of the hire's notes, when THEY ask you to change it " +
                "— 'can you edit this note', 'update my note about the deploy'. Never on your own " +
                "initiative: the note is theirs, and tidying words nobody asked you to tidy is how " +
                "a board stops being somebody's own. Read read_board for the card's id and its " +
                "current text, and pass the WHOLE new text, not only the part that changes — it " +
                "replaces the note. Keep everything they did not ask to change as it was. They see " +
                "the new text on a confirm button; afterwards the card says you changed it.",
            parameters = buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    put("card_id", cardIdParam())
                    putJsonObject("text") {
                        put("type", "string")
                        put("description", "The note's full new text. Markdown; first line is the heading.")
                    }
                }
                putJsonArray("required") {
                    add("card_id")
                    add("text")
                }
            },
        )
    }
}
