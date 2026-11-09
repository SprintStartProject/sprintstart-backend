package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.board.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardChange
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardState
import com.sprintstart.sprintstartbackend.board.model.entity.Board
import com.sprintstart.sprintstartbackend.board.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.board.repository.BoardRepository
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

/**
 * Which cards are on the hire's board and in what order: dismissing them and putting them in order.
 *
 * Both are done by the hire and, once confirmed, by the buddy on their behalf.
 */
internal class BoardArrangement(
    private val boardRepository: BoardRepository,
    private val boardCardRepository: BoardCardRepository,
    private val access: BoardAccess,
) {
    /**
     * Takes a card off the hire's board, for good.
     *
     * The row survives with [BoardCardState.DISMISSED]; it is never deleted. Both the
     * baseline and the mentor consult these rows before adding anything, so the removal sticks.
     *
     * Dismissing an already-dismissed card is a no-op.
     *
     * @param userId The caller, who must own the board the card is on.
     * @param cardId The card to remove.
     * @return False when no such card is on any board of theirs — the same answer for a card that
     * does not exist and one belonging to somebody else.
     */
    fun dismiss(userId: UserId, cardId: BoardCardId): Boolean {
        val card = boardCardRepository.findById(cardId.value).orElse(null) ?: return false
        val board = boardRepository.findById(card.boardId).orElse(null) ?: return false
        if (board.userId != userId.value) return false

        if (card.state != BoardCardState.DISMISSED) {
            card.state = BoardCardState.DISMISSED
            card.recordChange(BoardCardChange.DISMISSED, BoardActor.HIRE)
            boardCardRepository.save(card)
        }
        return true
    }

    /**
     * Takes cards off the hire's board on the buddy's behalf, once the hire has confirmed which.
     *
     * The same non-destructive dismissal as [dismiss] — the rows survive, so each is a state flip
     * away from coming back — scoped the way every buddy write is: to the project the proposal was
     * made on, and to cards still on that board. A card the hire already took off is skipped rather
     * than re-dismissed, so its attribution stays theirs.
     *
     * Any kind may go, the hire's own included: the hire can dismiss anything on their board, and
     * the buddy acting for them may do what they can. The confirm is what makes it theirs.
     *
     * @return How many cards actually left the board, so the reply can say exactly that.
     * @throws ResponseStatusException 404 when they are not a member of [projectId] or have no board
     * there.
     */
    fun dismissForBuddy(userId: UserId, projectId: ProjectId, cardIds: List<BoardCardId>): Int {
        val board = access.buddyBoardOrThrow(userId, projectId)
        val wanted = cardIds.map { it.value }.toSet()
        val dismissed = boardCardRepository
            .findAllByBoardId(board.id)
            .filter { it.id in wanted && it.state == BoardCardState.ACTIVE }
        if (dismissed.isEmpty()) return 0

        val now = Instant.now()
        dismissed.forEach { card ->
            card.state = BoardCardState.DISMISSED
            card.recordChange(BoardCardChange.DISMISSED, BoardActor.BUDDY, now)
        }
        boardCardRepository.saveAll(dismissed)
        return dismissed.size
    }

    /**
     * Puts the hire's cards in the order they asked for.
     *
     * Takes the whole order, not a from/to pair. Ids not on this board are ignored, not rejected.
     * Cards the request leaves out keep their relative order *after* the listed ones, so a client
     * that knows about only some of them cannot shuffle the rest.
     *
     * @throws ResponseStatusException 404 when they are not a member of that project.
     */
    fun reorder(userId: UserId, projectId: ProjectId, cardIds: List<BoardCardId>) {
        val board = access.existingBoardOf(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You have no board on that project")
        reorderOn(board, cardIds, BoardActor.HIRE)
    }

    /**
     * Puts the hire's cards in the order the buddy proposed and the hire confirmed.
     *
     * The same rule as [reorder] — the listed cards first, everything else after them in the order it
     * already had — scoped to the project the proposal was made on, with membership checked now
     * rather than when the proposal was made.
     *
     * @return How many cards actually moved. Zero is a real answer: the board was already in that
     * order.
     * @throws ResponseStatusException 404 when they are not a member of [projectId] or have no board
     * there.
     */
    fun reorderForBuddy(userId: UserId, projectId: ProjectId, cardIds: List<BoardCardId>): Int =
        reorderOn(access.buddyBoardOrThrow(userId, projectId), cardIds, BoardActor.BUDDY)

    /**
     * Writes an order to [board], attributing each card that actually moved to [by].
     *
     * "Moved" means its place *among the other cards* changed, not that its number did. Putting one
     * card first renumbers every card above it, and marking all of those would have the board claim
     * the buddy rearranged cards it never touched — or, on the hire's own drag, overwrite "your
     * buddy rewrote this" on cards the hire did not touch either. So the cards marked are the fewest
     * that explain the new order ([BoardCardOrder.moved]); the rest only have their number updated.
     *
     * A dismissed card is renumbered like any other but never marked — nobody moved a card that is
     * not on the board, and overwriting "dismissed" with "moved" would lose the one change on it
     * worth being able to see.
     */
    private fun reorderOn(board: Board, cardIds: List<BoardCardId>, by: BoardActor): Int {
        val cards = boardCardRepository.findAllByBoardId(board.id)
        val byId = cards.associateBy { it.id }
        val requested = cardIds.distinct().mapNotNull { byId[it.value] }
        val requestedIds = requested.map { it.id }.toSet()
        val rest = cards.filterNot { it.id in requestedIds }.sortedBy { it.position }
        val ordered = requested + rest
        val moved = BoardCardOrder.moved(ordered.filter { it.state == BoardCardState.ACTIVE })

        val now = Instant.now()
        ordered.forEachIndexed { index, card ->
            val renumbered = card.position != index
            card.position = index
            when {
                card.id in moved -> card.recordChange(BoardCardChange.MOVED, by, now)
                renumbered -> card.updatedAt = now
            }
        }
        boardCardRepository.saveAll(ordered)
        return moved.size
    }
}
