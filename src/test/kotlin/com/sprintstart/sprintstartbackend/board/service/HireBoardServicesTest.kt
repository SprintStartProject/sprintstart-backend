package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.board.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.external.enums.PlacementOutcome
import com.sprintstart.sprintstartbackend.board.external.model.HireBoard
import com.sprintstart.sprintstartbackend.board.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The board facades only take the board apart and hand each call to the service that owns it. */
class HireBoardServicesTest {
    private val boardService: BoardService = mockk()
    private val boardStructureService: BoardStructureService = mockk()
    private val openPullRequestReader: OpenPullRequestReader = mockk()

    private val reads = HireBoardService(boardService, boardStructureService, openPullRequestReader)
    private val actions = HireBoardActionsService(boardService)

    private val user = UserId(UUID.randomUUID())
    private val project = ProjectId(UUID.randomUUID())
    private val board = HireBoard(user, project)
    private val card = BoardCardId(UUID.randomUUID())

    @Test
    fun `reads answer with what the owning service answers`() {
        val pullRequest: AuthoredPullRequest = mockk()
        every { boardService.hasBoard(user, project) } returns true
        every { boardService.getBoard(user, project) } returns mockk()
        every { boardStructureService.read(user, project) } returns mockk()
        every { openPullRequestReader.openFor(project.value, "octocat") } returns listOf(pullRequest)
        every { openPullRequestReader.waitingHours(pullRequest) } returns 30

        assertEquals(true, reads.hasBoard(board))
        assertSame(boardService.getBoard(user, project), reads.boardOf(board))
        assertSame(boardStructureService.read(user, project), reads.structureOf(board))
        assertEquals(listOf(pullRequest), reads.openPullRequests(project, "octocat"))
        assertEquals(30, reads.waitingHours(pullRequest))
    }

    @Test
    fun `actions run on the hire's board with the buddy as the actor`() {
        val note = NoteCardRequest(text = "Ask about the deploy freeze")
        val cards = listOf(card)
        val lines = listOf("Read the runbook")
        every { boardService.place(HireBoard(user, project), BoardCardKind.DIAGRAM, "billing") } returns
            PlacementOutcome.PLACED
        every { boardService.addAuthoredCard(user, project, note, BoardActor.BUDDY) } returns mockk()
        every { boardService.editAuthoredCardForBuddy(HireBoard(user, project), card, note, "v1") } returns mockk()
        every { boardService.dismissForBuddy(user, project, cards) } returns 1
        every { boardService.reorderForBuddy(user, project, cards) } returns 2
        every { boardService.appendChecklistItems(user, project, card, lines) } returns mockk()
        every { boardService.tickChecklistItems(user, project, card, lines) } returns 3
        every { boardService.rewordChecklistItem(HireBoard(user, project), card, "Read", "Skim") } returns true

        assertEquals(PlacementOutcome.PLACED, actions.place(board, BoardCardKind.DIAGRAM, "billing"))
        assertSame(boardService.addAuthoredCard(user, project, note, BoardActor.BUDDY), actions.addCard(board, note))
        assertSame(
            boardService.editAuthoredCardForBuddy(HireBoard(user, project), card, note, "v1"),
            actions.editCard(board, card, note, "v1"),
        )
        assertEquals(1, actions.dismiss(board, cards))
        assertEquals(2, actions.reorder(board, cards))
        assertSame(
            boardService.appendChecklistItems(user, project, card, lines),
            actions.appendChecklistItems(board, card, lines),
        )
        assertEquals(3, actions.tickChecklistItems(board, card, lines))
        assertEquals(true, actions.rewordChecklistItem(board, card, "Read", "Skim"))
    }
}
