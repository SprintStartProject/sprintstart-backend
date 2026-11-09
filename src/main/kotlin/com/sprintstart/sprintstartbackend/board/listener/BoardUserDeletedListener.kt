package com.sprintstart.sprintstartbackend.board.listener

import com.sprintstart.sprintstartbackend.board.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.board.repository.BoardRepository
import com.sprintstart.sprintstartbackend.board.repository.BoardStructureRepository
import com.sprintstart.sprintstartbackend.user.external.events.OnUserDeleted
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import org.springframework.stereotype.Component

/**
 * Erases a deleted user's boards with their cards and arrangement.
 *
 * The arrangement holds what this person named the parts of their board and which sentences they
 * marked, so it goes with the board. Cards and the arrangement go before the boards they point at.
 */
@Component
class BoardUserDeletedListener(
    private val boardRepository: BoardRepository,
    private val boardCardRepository: BoardCardRepository,
    private val boardStructureRepository: BoardStructureRepository,
) {
    @OnUserDeleted
    fun onUserDeleted(event: UserDeletedEvent) {
        val boardIds = boardRepository.findAllByUserId(event.userId).map { it.id }
        boardIds.forEach { boardCardRepository.deleteAllByBoardId(it) }
        // An empty `IN ()` is a query some databases refuse outright.
        if (boardIds.isNotEmpty()) boardStructureRepository.deleteAllByBoardIdIn(boardIds)
        boardRepository.deleteAllByUserId(event.userId)
    }
}
