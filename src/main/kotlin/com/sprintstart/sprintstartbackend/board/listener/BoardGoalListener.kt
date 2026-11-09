package com.sprintstart.sprintstartbackend.board.listener

import com.sprintstart.sprintstartbackend.board.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.service.BoardService
import com.sprintstart.sprintstartbackend.starterwork.external.enums.GoalClaimant
import com.sprintstart.sprintstartbackend.starterwork.external.event.GoalClaimedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Pins the current-task card when a hire claims a goal, and revives it if they had dismissed it.
 *
 * However the task was claimed, claiming it says this is what the hire is working on. Runs in the
 * claim's transaction, so a claim and its card cannot end up disagreeing.
 */
@Component
class BoardGoalListener(
    private val boardService: BoardService,
) {
    @EventListener
    fun onGoalClaimed(event: GoalClaimedEvent) {
        boardService.placeOrRevive(
            event.userId,
            event.projectId,
            BoardCardKind.CURRENT_TASK,
            event.claimedBy.toBoardActor(),
        )
    }

    private fun GoalClaimant.toBoardActor(): BoardActor =
        when (this) {
            GoalClaimant.HIRE -> BoardActor.HIRE
            GoalClaimant.BUDDY -> BoardActor.BUDDY
        }
}
