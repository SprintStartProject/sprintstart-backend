package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.board.model.entity.Board
import com.sprintstart.sprintstartbackend.board.repository.BoardRepository
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

/**
 * Where a hire's board and their membership are looked up, for the services that write to the board.
 */
internal class BoardAccess(
    private val boardRepository: BoardRepository,
    private val projectMembershipApi: ProjectMembershipApi,
) {
    /**
     * The board a buddy write to the arrangement — a dismissal, an order — lands on.
     *
     * The same staleness rule as [buddyEditableCardOrThrow]: a member of [projectId] now, checked
     * before anything is read, and only ever that project's board.
     *
     * @throws ResponseStatusException 404 when they are not a member, or have no board there.
     */
    fun buddyBoardOrThrow(userId: UserId, projectId: ProjectId): Board {
        memberOrNull(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project")
        return existingBoardOf(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You have no board on that project")
    }

    fun memberOrNull(userId: UserId, projectId: ProjectId): ProjectMember? =
        projectMembershipApi.getProjectMembers(projectId.value).firstOrNull { it.userId == userId.value }

    fun existingBoardOf(userId: UserId, projectId: ProjectId): Board? =
        boardRepository.findByUserIdAndProjectId(userId.value, projectId.value)

    /** The hire's board on [projectId], created empty the first time anything needs it. */
    fun boardOf(userId: UserId, projectId: ProjectId): Board =
        existingBoardOf(userId, projectId)
            ?: boardRepository.save(Board(userId = userId.value, projectId = projectId.value))
}
