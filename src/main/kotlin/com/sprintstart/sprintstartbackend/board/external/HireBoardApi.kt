package com.sprintstart.sprintstartbackend.board.external

import com.sprintstart.sprintstartbackend.board.external.model.HireBoard
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardStructureResponse
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId

/** A hire's board and open pull requests as the buddy reads them. */
interface HireBoardApi {
    /** Whether the hire has a board there at all, without creating one by asking. */
    fun hasBoard(board: HireBoard): Boolean

    /** The board with its cards read live, created on first read; null when the hire is not on the project. */
    fun boardOf(board: HireBoard): BoardResponse?

    /** How the hire arranged the board, or the empty arrangement; null when they are not on the project. */
    fun structureOf(board: HireBoard): BoardStructureResponse?

    /** [login]'s open pull requests on [projectId], longest-waiting first; none without a login. */
    fun openPullRequests(projectId: ProjectId, login: String?): List<AuthoredPullRequest>

    /** How long [pullRequest] has waited for a first response, or null when it is not waiting. */
    fun waitingHours(pullRequest: AuthoredPullRequest): Long?
}
