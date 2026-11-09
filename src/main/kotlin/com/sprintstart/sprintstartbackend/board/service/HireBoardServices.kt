package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.board.external.HireBoardActionsApi
import com.sprintstart.sprintstartbackend.board.external.HireBoardApi
import com.sprintstart.sprintstartbackend.board.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.external.enums.PlacementOutcome
import com.sprintstart.sprintstartbackend.board.external.model.HireBoard
import com.sprintstart.sprintstartbackend.board.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardStructureResponse
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service

/** [HireBoardApi] over the board, its arrangement and the pull-request reader. */
@Service
class HireBoardService(
    private val boardService: BoardService,
    private val boardStructureService: BoardStructureService,
    private val openPullRequestReader: OpenPullRequestReader,
) : HireBoardApi {
    override fun hasBoard(board: HireBoard): Boolean = boardService.hasBoard(board.userId, board.projectId)

    override fun boardOf(board: HireBoard): BoardResponse? = boardService.getBoard(board.userId, board.projectId)

    override fun structureOf(board: HireBoard): BoardStructureResponse? =
        boardStructureService.read(board.userId, board.projectId)

    override fun openPullRequests(projectId: ProjectId, login: String?): List<AuthoredPullRequest> =
        openPullRequestReader.openFor(projectId.value, login)

    override fun waitingHours(pullRequest: AuthoredPullRequest): Long? = openPullRequestReader.waitingHours(pullRequest)
}

/** [HireBoardActionsApi] over the board service, with the buddy as the actor. */
@Service
class HireBoardActionsService(
    private val boardService: BoardService,
) : HireBoardActionsApi {
    override fun place(board: HireBoard, kind: BoardCardKind, subject: String?): PlacementOutcome =
        boardService.place(board, kind, subject)

    override fun addCard(board: HireBoard, request: AuthoredCardRequest): BoardCardResponse =
        boardService.addAuthoredCard(board.userId, board.projectId, request, by = BoardActor.BUDDY)

    override fun editCard(
        board: HireBoard,
        cardId: BoardCardId,
        request: AuthoredCardRequest,
        basedOn: String?,
    ): BoardCardResponse =
        boardService.editAuthoredCardForBuddy(board, cardId, request, basedOn)

    override fun dismiss(board: HireBoard, cardIds: List<BoardCardId>): Int =
        boardService.dismissForBuddy(board.userId, board.projectId, cardIds)

    override fun reorder(board: HireBoard, cardIds: List<BoardCardId>): Int =
        boardService.reorderForBuddy(board.userId, board.projectId, cardIds)

    override fun appendChecklistItems(board: HireBoard, cardId: BoardCardId, lines: List<String>): BoardCardResponse =
        boardService.appendChecklistItems(board.userId, board.projectId, cardId, lines)

    override fun tickChecklistItems(board: HireBoard, cardId: BoardCardId, lines: List<String>): Int =
        boardService.tickChecklistItems(board.userId, board.projectId, cardId, lines)

    override fun rewordChecklistItem(board: HireBoard, cardId: BoardCardId, before: String, after: String): Boolean =
        boardService.rewordChecklistItem(board, cardId, before, after)
}
