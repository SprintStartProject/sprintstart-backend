package com.sprintstart.sprintstartbackend.board.listener

import com.sprintstart.sprintstartbackend.board.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.service.BoardService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.enums.GoalClaimant
import com.sprintstart.sprintstartbackend.starterwork.external.event.GoalClaimedEvent
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.util.UUID
import java.util.function.Supplier

class BoardGoalListenerTest {
    private val boardService: BoardService = mockk(relaxed = true)
    private val listener = BoardGoalListener(boardService)

    private val userId = UserId(UUID.randomUUID())
    private val projectId = ProjectId(UUID.randomUUID())

    /** `placeOrRevive`, not `place`: a card the hire dismissed while it had nothing on it comes back. */
    @Test
    fun `a goal the hire claimed pins or revives the current-task card as the hire's change`() {
        listener.onGoalClaimed(GoalClaimedEvent(userId, projectId, GoalClaimant.HIRE))

        verify { boardService.placeOrRevive(userId, projectId, BoardCardKind.CURRENT_TASK, BoardActor.HIRE) }
    }

    /** The claim only publishes; without the listener registered, no card would ever be pinned. */
    @Test
    fun `receives the event a claim publishes through the application context`() {
        AnnotationConfigApplicationContext().use { context ->
            context.registerBean("boardService", BoardService::class.java, Supplier { boardService })
            context.register(BoardGoalListener::class.java)
            context.refresh()

            context.publishEvent(GoalClaimedEvent(userId, projectId, GoalClaimant.HIRE))
        }

        verify(exactly = 1) {
            boardService.placeOrRevive(userId, projectId, BoardCardKind.CURRENT_TASK, BoardActor.HIRE)
        }
    }

    @Test
    fun `a goal the buddy claimed records the buddy as the card's latest change`() {
        listener.onGoalClaimed(GoalClaimedEvent(userId, projectId, GoalClaimant.BUDDY))

        verify { boardService.placeOrRevive(userId, projectId, BoardCardKind.CURRENT_TASK, BoardActor.BUDDY) }
    }
}
