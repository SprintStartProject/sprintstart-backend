package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.board.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardChange
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardState
import com.sprintstart.sprintstartbackend.board.external.enums.PlacementOutcome
import com.sprintstart.sprintstartbackend.board.external.model.HireBoard
import com.sprintstart.sprintstartbackend.board.model.entity.Board
import com.sprintstart.sprintstartbackend.board.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.board.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.path.external.PathReadApi
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import java.time.Instant

/**
 * Putting a card on the hire's board for them: the mentor's placements, and the hire's own grab of a task.
 */
internal class BoardPlacement(
    private val boardCardRepository: BoardCardRepository,
    private val pathReadApi: PathReadApi,
    private val access: BoardAccess,
) {
    private companion object {
        /**
         * Long enough for any real question, short enough that a rambling one cannot become a card
         * title nobody can read. Matches the cap the AI service applies to the same string.
         */
        const val MAX_SUBJECT_LENGTH = 200

        val WHITESPACE = Regex("\\s+")
    }

    /**
     * Puts a card on this hire's board on the mentor's behalf. Applied directly, not confirm-gated.
     *
     * Every refusal returns as a sentence, never as silence. It refuses:
     * - a card the hire dismissed, which is never put back;
     * - a card already there, left alone with its position;
     * - a kind with [BoardCardKind.takesSubject] and no subject;
     * - a [BoardCardKind.PATH_STEP] whose subject names no real step of this hire's path.
     *
     * @param userId The hire whose board it is.
     * @param projectId The project the board belongs to.
     * @param kind The card to place.
     * @param subject What a [BoardCardKind.DIAGRAM] is a diagram of, or the title of the step for a
     *   [BoardCardKind.PATH_STEP]. Required for a kind with [BoardCardKind.takesSubject] and ignored
     *   for every other.
     * @return What happened, in a form the caller can turn into a line for the model.
     */
    fun place(
        hire: HireBoard,
        kind: BoardCardKind,
        subject: String? = null,
        by: BoardActor = BoardActor.BUDDY,
    ): PlacementOutcome {
        val (userId, projectId) = hire
        access.memberOrNull(userId, projectId) ?: return PlacementOutcome.NOT_A_MEMBER

        val cleanSubject = subject?.let { normaliseSubject(it) }?.takeIf { it.isNotBlank() }
        if (kind.takesSubject && cleanSubject == null) return PlacementOutcome.NEEDS_A_SUBJECT

        // A diagram's subject is asserted, free text; a path step's subject is resolved server-side
        // and stored as the step's own id, so a renamed step keeps its card and "the same step
        // twice" is an exact match regardless of how the mentor phrased it.
        val storedSubject = if (kind == BoardCardKind.PATH_STEP) {
            val resolved = cleanSubject?.let { pathReadApi.resolveStep(userId, it) }
                ?: return PlacementOutcome.NO_SUCH_STEP
            resolved.id.toString()
        } else {
            cleanSubject
        }

        return placeCard(access.boardOf(userId, projectId), kind, storedSubject, by)
    }

    /**
     * Places a card of [kind], or brings it back if the hire dismissed it before.
     *
     * The one exception to dismissal being sticky, and it is only for the hire's own act: when they
     * grab a task, "this is what I'm working on" is exactly what the current-task card says, so a
     * card they dismissed back when it had nothing on it has to return. The mentor never gets this —
     * [place] stays the only thing it can call.
     *
     * Only for kinds without a subject, where one row per kind is the whole identity.
     *
     * @param by Who placed or brought back the card: the hire for a grab by hand, the buddy for one
     *   the hire confirmed. Recorded as the card's latest change either way.
     */
    fun placeRevivingDismissed(
        userId: UserId,
        projectId: ProjectId,
        kind: BoardCardKind,
        by: BoardActor,
    ): PlacementOutcome {
        require(!kind.takesSubject) { "$kind is identified by its subject; revive it through place()" }
        val outcome = place(HireBoard(userId, projectId), kind, by = by)
        if (outcome != PlacementOutcome.DISMISSED_BY_HIRE) return outcome

        val board = access.existingBoardOf(userId, projectId) ?: return outcome
        val card = boardCardRepository
            .findAllByBoardId(board.id)
            .firstOrNull { it.kind == kind && it.state == BoardCardState.DISMISSED }
            ?: return outcome
        // Back on the board is a placement by whoever did it, so the card stops saying it was dismissed.
        val now = Instant.now()
        card.state = BoardCardState.ACTIVE
        card.placedAt = now
        card.recordChange(BoardCardChange.CREATED, by, now)
        boardCardRepository.save(card)
        return PlacementOutcome.PLACED
    }

    private fun placeCard(
        board: Board,
        kind: BoardCardKind,
        storedSubject: String?,
        by: BoardActor,
    ): PlacementOutcome {
        val existing = boardCardRepository.findAllByBoardId(board.id)

        // For a kind that takes no subject, one row per kind is the whole identity. A kind that
        // does is identified by its subject as well: two subjects are two different cards, and
        // repurposing an existing one into a new subject would take away something the hire kept.
        existing.firstOrNull { it.kind == kind && it.matchesSubject(storedSubject) }?.let { card ->
            return if (card.state == BoardCardState.DISMISSED) {
                PlacementOutcome.DISMISSED_BY_HIRE
            } else {
                PlacementOutcome.ALREADY_THERE
            }
        }

        val now = Instant.now()
        boardCardRepository.save(
            BoardCard(
                boardId = board.id,
                kind = kind,
                owner = BoardCardOwner.AI,
                position = (existing.maxOfOrNull { it.position } ?: -1) + 1,
                // Dated, because the board says "your buddy put this here" only about cards it
                // actually did.
                placedAt = now,
                subject = storedSubject.takeIf { kind.takesSubject },
            ).apply { recordChange(BoardCardChange.CREATED, by, now) },
        )
        return PlacementOutcome.PLACED
    }

    /**
     * Whether this row is the same card as one of [kind] with [subject].
     *
     * Case- and whitespace-insensitive, so a dismissal sticks against a re-phrasing.
     */
    private fun BoardCard.matchesSubject(subject: String?): Boolean =
        !kind.takesSubject ||
            this.subject?.let { normaliseSubject(it).equals(subject, ignoreCase = true) } == true

    private fun normaliseSubject(subject: String): String =
        subject.trim().replace(WHITESPACE, " ").take(MAX_SUBJECT_LENGTH)
}
