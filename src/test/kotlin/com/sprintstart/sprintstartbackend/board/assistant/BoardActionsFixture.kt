package com.sprintstart.sprintstartbackend.board.assistant

import com.sprintstart.sprintstartbackend.assistant.external.HireAction
import com.sprintstart.sprintstartbackend.board.service.BoardService
import com.sprintstart.sprintstartbackend.board.service.HireBoardActionsService
import com.sprintstart.sprintstartbackend.board.service.hireBoardOver

/** Every board action, in `@Order` order, over the real board facades around [boardService]. */
fun boardActionsOver(boardService: BoardService): List<HireAction> {
    val board = hireBoardOver(boardService)
    val actions = HireBoardActionsService(boardService)
    return listOf(
        PlaceChecklistAction(actions),
        AmendChecklistAction(actions),
        TickChecklistItemsAction(actions),
        RewordChecklistItemAction(actions),
        PlaceNoteAction(actions),
        PlaceLinkAction(actions),
        EditNoteAction(board, actions),
        EditLinkAction(board, actions),
        EditChecklistAction(board, actions),
        DismissCardsAction(board, actions),
        ReorderCardsAction(board, actions),
    )
}
