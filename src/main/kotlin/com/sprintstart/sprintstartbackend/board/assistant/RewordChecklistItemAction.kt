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
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException

/**
 * Offers to rewrite one line of a checklist, when the hire asks for that line to be clearer.
 *
 * Deliberately its own action rather than something [AmendChecklistAction] could also do. That one is
 * append-only *by construction*: it is never handed the existing lines, so there is no version of adding
 * a step in which a line quietly changes on the way. Rewording is a thing the hire asks for, about a line
 * they name, and the confirm shows both wordings — which is what makes it an edit they can see rather
 * than one they would have to go looking for.
 */
@Component
@Order(HireActionOrder.REWORD_CHECKLIST_ITEM)
class RewordChecklistItemAction(
    private val boardActions: HireBoardActionsApi,
) : HireAction {
    override val spec = SPEC
    override val scope = HireActionScope.PROJECT
    override val gerund = "reword a line on your board"

    /**
     * Both wordings travel with the proposal, so the confirm can show the change rather than assert one.
     * Refuses a rewording that says nothing, and a rewording that says exactly what the line already said.
     */
    override fun propose(call: BuddyToolCallDto, context: HireActionContext): HireActionOutcome {
        val cardId = call.uuidArgument("card_id") ?: return refused(NO_CHECKLIST_ID)
        val before = call.textArgument("line")
        val after = call.textArgument("reworded").take(MAX_CHECKLIST_ITEM_LENGTH)
        return when {
            before.isEmpty() || after.isEmpty() -> refused("Both the line and its rewording are needed.")
            before.equals(after, ignoreCase = true) ->
                refused("That is what the line already says — there is nothing to change.")
            else -> offer(
                context,
                HireActionProposal(
                    action = REWORD_CHECKLIST_ITEM,
                    label = LABEL,
                    question = null,
                    cardId = cardId,
                    lineBefore = before,
                    lineAfter = after,
                ),
            )
        }
    }

    /** Rewrites the named line, and can do nothing else to the card. */
    override suspend fun perform(
        request: HireActionRequest,
        context: HireActionContext,
        authId: AuthId,
    ): BuddyActionResponse {
        val cardId = request.cardId
        val before = request.lineBefore
        val after = request.lineAfter
        if (cardId == null || before.isNullOrBlank() || after.isNullOrBlank()) {
            return BuddyActionResponse(ok = false, message = "There was no line to reword.")
        }
        val reworded = after.take(MAX_CHECKLIST_ITEM_LENGTH)

        return try {
            if (boardActions.rewordChecklistItem(context.board, BoardCardId(cardId), before, reworded)) {
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

    companion object {
        const val REWORD_CHECKLIST_ITEM = "reword_checklist_item"
        const val LABEL = "Reword this line"

        val SPEC = BuddyToolSpecDto(
            name = REWORD_CHECKLIST_ITEM,
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
    }
}
