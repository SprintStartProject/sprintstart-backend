package com.sprintstart.sprintstartbackend.board.listener

import com.sprintstart.sprintstartbackend.board.model.entity.Board
import com.sprintstart.sprintstartbackend.board.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.board.repository.BoardRepository
import com.sprintstart.sprintstartbackend.board.repository.BoardStructureRepository
import com.sprintstart.sprintstartbackend.user.external.events.UserDeletedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import java.util.UUID

class BoardUserDeletedListenerTest {
    private val boardRepository: BoardRepository = mockk(relaxed = true)
    private val boardCardRepository: BoardCardRepository = mockk(relaxed = true)
    private val boardStructureRepository: BoardStructureRepository = mockk(relaxed = true)
    private val listener = BoardUserDeletedListener(boardRepository, boardCardRepository, boardStructureRepository)
    private val userId = UUID.randomUUID()

    /**
     * How this person arranged their board is a description of them: it holds what they called the
     * parts of it and which sentences they marked. It goes with the board, and before it.
     */
    @Test
    fun `cards and the arrangement go before the boards they belong to`() {
        val board = Board(userId = userId, projectId = UUID.randomUUID())
        every { boardRepository.findAllByUserId(userId) } returns listOf(board)

        listener.onUserDeleted(UserDeletedEvent(userId))

        verifyOrder {
            boardCardRepository.deleteAllByBoardId(board.id)
            boardStructureRepository.deleteAllByBoardIdIn(listOf(board.id))
            boardRepository.deleteAllByUserId(userId)
        }
    }

    /** An empty `IN ()` is a query some databases refuse outright and the rest run for nothing. */
    @Test
    fun `a user without a board asks for no arrangement to be deleted`() {
        every { boardRepository.findAllByUserId(userId) } returns emptyList()

        listener.onUserDeleted(UserDeletedEvent(userId))

        verify(exactly = 0) { boardStructureRepository.deleteAllByBoardIdIn(any()) }
        verify { boardRepository.deleteAllByUserId(userId) }
    }
}
