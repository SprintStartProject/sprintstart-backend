package com.sprintstart.sprintstartbackend.board.external

import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.external.enums.PlacementOutcome
import com.sprintstart.sprintstartbackend.board.external.model.HireBoard
import com.sprintstart.sprintstartbackend.board.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId

/**
 * What the buddy changes on a hire's board. Every write but [place] runs behind a confirm the hire
 * pressed, and is scoped to cards still on that board.
 */
interface HireBoardActionsApi {
    /** Puts a [kind] card on the board directly; every refusal comes back as a [PlacementOutcome]. */
    fun place(board: HireBoard, kind: BoardCardKind, subject: String?): PlacementOutcome

    /** Adds a note, link or checklist the buddy wrote; the hire owns it. 404 off the project, 400 when empty. */
    fun addCard(board: HireBoard, request: AuthoredCardRequest): BoardCardResponse

    /** Replaces one of the hire's cards whole, if it still has the fingerprint [basedOn] (see [BoardCardVersion]). */
    fun editCard(
        board: HireBoard,
        cardId: BoardCardId,
        request: AuthoredCardRequest,
        basedOn: String?,
    ): BoardCardResponse

    /** Takes cards off the board without deleting them; how many went. */
    fun dismiss(board: HireBoard, cardIds: List<BoardCardId>): Int

    /** Puts the listed cards first, the rest after in their order; how many moved. */
    fun reorder(board: HireBoard, cardIds: List<BoardCardId>): Int

    /** Adds [lines] to the end of a checklist and changes nothing else on it. */
    fun appendChecklistItems(board: HireBoard, cardId: BoardCardId, lines: List<String>): BoardCardResponse

    /** Ticks the lines whose words match [lines], never unticks; how many were ticked. */
    fun tickChecklistItems(board: HireBoard, cardId: BoardCardId, lines: List<String>): Int

    /** Rewrites the one line reading [before], keeping its id and tick; false when none or several match. */
    fun rewordChecklistItem(board: HireBoard, cardId: BoardCardId, before: String, after: String): Boolean
}
