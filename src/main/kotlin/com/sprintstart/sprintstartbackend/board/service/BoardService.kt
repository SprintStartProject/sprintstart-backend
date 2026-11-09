package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.arrival.external.ArrivalReadApi
import com.sprintstart.sprintstartbackend.arrival.model.response.arrival.ArrivalStepResponse
import com.sprintstart.sprintstartbackend.board.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardChange
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardState
import com.sprintstart.sprintstartbackend.board.external.enums.PlacementOutcome
import com.sprintstart.sprintstartbackend.board.external.model.HireBoard
import com.sprintstart.sprintstartbackend.board.model.entity.Board
import com.sprintstart.sprintstartbackend.board.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.board.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.BoardResponse
import com.sprintstart.sprintstartbackend.board.model.response.board.PathStepContent
import com.sprintstart.sprintstartbackend.board.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.board.repository.BoardDiagramRepository
import com.sprintstart.sprintstartbackend.board.repository.BoardRepository
import com.sprintstart.sprintstartbackend.path.external.PathReadApi
import com.sprintstart.sprintstartbackend.shared.ids.BoardCardId
import com.sprintstart.sprintstartbackend.shared.ids.OnboardingTaskId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.starterwork.external.HireStarterWorkApi
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * A hire's board: which cards are on it, and what each one currently says.
 *
 * A read ensures the cards relevant to this hire exist — idempotently, and never re-adding one they
 * dismissed — then has [BoardCardHydrator] fill each surviving card with a live read. Relevance is
 * re-evaluated on every load, not only at creation.
 */
@Suppress("TooManyFunctions") // Every write a hire or the buddy makes to a board, each with its refusals.
@Service
class BoardService(
    private val boardRepository: BoardRepository,
    private val boardCardRepository: BoardCardRepository,
    private val projectMembershipApi: ProjectMembershipApi,
    private val hireStarterWork: HireStarterWorkApi,
    private val boardDiagramRepository: BoardDiagramRepository,
    private val hydrator: BoardCardHydrator,
    private val arrivalSteps: ArrivalReadApi,
    private val pathReadApi: PathReadApi,
) {
    private val access = BoardAccess(boardRepository, projectMembershipApi)
    private val placement = BoardPlacement(boardCardRepository, pathReadApi, access)
    private val arrangement = BoardArrangement(boardRepository, boardCardRepository, access)
    private val authoredCards = BoardAuthoredCards(boardRepository, boardCardRepository, access, hydrator)

    /**
     * Whether this hire has a board on this project at all.
     *
     * For the callers that must not bring one into existence by asking about it. [getBoard] creates
     * the board and seeds it, which is what a hire opening the page should get and not what
     * something merely looking at the board should cause — a board is a thing the hire has, and it
     * should start existing because they went to it.
     *
     * @param userId The hire.
     * @param projectId The project the board would belong to.
     * @return Whether a board row exists. Says nothing about membership, and nothing about whether
     * there is anything on it.
     */
    @Transactional(readOnly = true)
    fun hasBoard(userId: UserId, projectId: ProjectId): Boolean =
        boardRepository.existsByUserIdAndProjectId(userId.value, projectId.value)

    /**
     * This hire's board on this project, cards hydrated.
     *
     * @param userId The hire.
     * @param projectId The project the board belongs to.
     * @return The board, created on first read, or null when this hire is not a member of that
     * project — a board without a membership behind it has nothing to be about.
     */
    @Transactional
    fun getBoard(userId: UserId, projectId: ProjectId): BoardResponse? {
        val member = access.memberOrNull(userId, projectId) ?: return null
        val board = access.boardOf(userId, projectId)

        // Read once and reuse: it decides whether the card is ensured at all, and then fills it.
        val arrivalSteps = arrivalSteps.forHire(userId)
        val cards = ensureRelevantCards(board, arrivalSteps.isNotEmpty())
        // Whether the hire is on a task at all, for the pin. Read through the same
        // [HireStarterWorkApi.currentTaskOf] the card's content comes from, so the pin and the card agree.
        val onATask = hireStarterWork.currentTaskOf(userId, projectId) != null
        // One query for every diagram on the board, and the stored picture rather than a fresh one:
        // assembling costs a model call. The client revalidates afterwards.
        val diagrams = boardDiagramRepository
            .findAllByCardIdIn(cards.filter { it.kind == BoardCardKind.DIAGRAM }.map { it.id })
            .associateBy { it.cardId }
        // One read for every PATH_STEP card, skipped when the board holds none.
        val pathSteps = if (cards.any { it.kind == BoardCardKind.PATH_STEP }) {
            pathReadApi.stepsById(userId)
        } else {
            emptyMap()
        }
        // One ranking per read, shared by the suggestions and pool cards so they agree and the board pays
        // once; lazy, so a board with neither pays nothing.
        val matches by lazy { hireStarterWork.rankedTasksFor(userId, projectId) }

        return BoardResponse(
            boardId = board.id,
            projectId = projectId.value,
            cards = cards
                .filter { it.state == BoardCardState.ACTIVE }
                .sortedWith(attentionOrder(arrivalSteps, onATask))
                .map {
                    val shared = SharedBoardReads(diagrams[it.id], arrivalSteps, pathSteps) { matches }
                    hydrator.render(it, member, projectId, shared)
                },
        )
    }

    /**
     * The hire's own order, except that what needs them now comes first: outstanding arrival steps,
     * then the task they are on. Arrival outranks the current task.
     *
     * A sort applied on read, never a write to `position`. The hire's arrangement is
     * untouched underneath and returns exactly as they left it once the last step settles or the
     * task is done. Dismissal still wins over the pin: a dismissed card stays gone.
     *
     * Nothing here caps or archives cards. Removing a card automatically would break sticky
     * dismissal, which is the hire's alone.
     *
     * @param onATask Whether the hire actually has a task. A `CURRENT_TASK` card reading "nothing
     *   claimed yet" is not pinned.
     */
    private fun attentionOrder(
        arrivalSteps: List<ArrivalStepResponse>,
        onATask: Boolean,
    ): Comparator<BoardCard> {
        val anythingOutstanding = arrivalSteps.any { !it.settled }

        return compareBy<BoardCard> {
            when {
                anythingOutstanding && it.kind == BoardCardKind.ARRIVAL_STEPS -> 0
                onATask && it.kind == BoardCardKind.CURRENT_TASK -> 1
                else -> 2
            }
        }.thenBy { it.position }
    }

    /**
     * Ticks or unticks one task of a [BoardCardKind.PATH_STEP] card, writing back to the path
     * itself.
     *
     * The one live card the hire may change: everywhere else, a card is read-only because it is a
     * live read, but a task ticked here and the same task still open on the path page would be two
     * different facts about the same piece of work. So this writes through [PathReadApi.setTaskFinished],
     * the single place [com.sprintstart.sprintstartbackend.path.model.entity.OnboardingTask.finished]
     * is set, and returns the re-hydrated card content — never the entity.
     *
     * The step's own status is never touched here, the same as [PathReadApi.setTaskFinished]
     * leaves it. Idempotent: setting [done] to what it already is still returns the current content.
     *
     * @throws ResponseStatusException 404 for a card that does not exist, belongs to somebody else,
     * is not a `PATH_STEP`, no longer resolves to a real step, or names a task that is not one of
     * that step's — the same answer for all of them, so a foreign id proves nothing about what is
     * really there.
     */
    @Transactional
    fun tickPathStepTask(
        userId: UserId,
        cardId: BoardCardId,
        taskId: OnboardingTaskId,
        done: Boolean,
    ): PathStepContent {
        val card = boardCardRepository.findById(cardId.value).orElse(null) ?: noSuchPathStepCard()
        val board = boardRepository.findById(card.boardId).orElse(null) ?: noSuchPathStepCard()
        if (board.userId != userId.value) noSuchPathStepCard()
        if (card.kind != BoardCardKind.PATH_STEP) noSuchPathStepCard()

        val stepId = card.subject?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val resolved = stepId?.let { pathReadApi.stepsById(userId)[it] } ?: noSuchPathStepCard()
        if (resolved.tasks.none { it.id == taskId.value }) noSuchPathStepCard()

        pathReadApi.setTaskFinished(userId, taskId, done)
        card.recordChange(BoardCardChange.TICKED, BoardActor.HIRE)
        boardCardRepository.save(card)

        return pathReadApi.stepsById(userId)[resolved.id]?.toContent() ?: noSuchPathStepCard()
    }

    /**
     * The one refusal [tickPathStepTask] gives, for every reason it can be refused: a foreign card,
     * a missing one, the wrong kind, or a task that is not the step's. All of them come back
     * indistinguishable, the same as [editableCardOrThrow]'s refusal does, so a foreign id proves
     * nothing about what is really there.
     */
    private fun noSuchPathStepCard(): Nothing =
        throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such card on your board")

    /**
     * Which cards belong on [board] for this hire, creating any that are missing.
     *
     * A card whose kind already has a row is left exactly as it is, dismissed rows included.
     *
     * @return Every card row on the board, including ones the hire has dismissed.
     */
    private fun ensureRelevantCards(
        board: Board,
        hasArrivalSteps: Boolean,
    ): List<BoardCard> {
        val existing = boardCardRepository.findAllByBoardId(board.id)
        val present = existing.map { it.kind }.toSet()
        val missing = relevantKinds(hasArrivalSteps).filterNot { it in present }
        if (missing.isEmpty()) return existing

        // New cards append after everything already there, so ensuring never reshuffles the board.
        var nextPosition = (existing.maxOfOrNull { it.position } ?: -1) + 1
        val added = missing.map { kind ->
            BoardCard(
                boardId = board.id,
                kind = kind,
                // Placed for the hire, not by them: they may dismiss it, they do not edit it.
                owner = BoardCardOwner.AI,
                position = nextPosition++,
            )
        }
        return existing + boardCardRepository.saveAll(added)
    }

    /**
     * The card kinds worth showing this hire, in the order they are first placed.
     */
    private fun relevantKinds(hasArrivalSteps: Boolean): List<BoardCardKind> =
        BoardCardKind.entries.filter {
            it.placement == BoardCardKind.Placement.BASELINE &&
                // An arrival card is meaningful for every hire and simply has nothing to say until
                // somebody authors a step.
                (it != BoardCardKind.ARRIVAL_STEPS || hasArrivalSteps)
        }

    /** See [BoardPlacement.place]. */
    @Transactional
    fun place(
        hire: HireBoard,
        kind: BoardCardKind,
        subject: String? = null,
        by: BoardActor = BoardActor.BUDDY,
    ): PlacementOutcome = placement.place(hire, kind, subject, by)

    /** See [BoardPlacement.placeRevivingDismissed]. */
    @Transactional
    fun placeOrRevive(userId: UserId, projectId: ProjectId, kind: BoardCardKind, by: BoardActor): PlacementOutcome =
        placement.placeRevivingDismissed(userId, projectId, kind, by)

    /** See [BoardArrangement.dismiss]. */
    @Transactional
    fun dismiss(userId: UserId, cardId: BoardCardId): Boolean = arrangement.dismiss(userId, cardId)

    /** See [BoardArrangement.dismissForBuddy]. */
    @Transactional
    fun dismissForBuddy(userId: UserId, projectId: ProjectId, cardIds: List<BoardCardId>): Int =
        arrangement.dismissForBuddy(userId, projectId, cardIds)

    /** See [BoardArrangement.reorder]. */
    @Transactional
    fun reorder(userId: UserId, projectId: ProjectId, cardIds: List<BoardCardId>) =
        arrangement.reorder(userId, projectId, cardIds)

    /** See [BoardArrangement.reorderForBuddy]. */
    @Transactional
    fun reorderForBuddy(userId: UserId, projectId: ProjectId, cardIds: List<BoardCardId>): Int =
        arrangement.reorderForBuddy(userId, projectId, cardIds)

    /** See [BoardAuthoredCards.addAuthoredCard]. */
    @Transactional
    fun addAuthoredCard(
        userId: UserId,
        projectId: ProjectId,
        request: AuthoredCardRequest,
        by: BoardActor = BoardActor.HIRE,
    ): BoardCardResponse = authoredCards.addAuthoredCard(userId, projectId, request, by)

    /** See [BoardAuthoredCards.editAuthoredCard]. */
    @Transactional
    fun editAuthoredCard(userId: UserId, cardId: BoardCardId, request: AuthoredCardRequest): BoardCardResponse =
        authoredCards.editAuthoredCard(userId, cardId, request)

    /** See [BoardAuthoredCards.appendChecklistItems]. */
    @Transactional
    fun appendChecklistItems(
        userId: UserId,
        projectId: ProjectId,
        cardId: BoardCardId,
        lines: List<String>,
    ): BoardCardResponse = authoredCards.appendChecklistItems(userId, projectId, cardId, lines)

    /** See [BoardAuthoredCards.tickChecklistItems]. */
    @Transactional
    fun tickChecklistItems(userId: UserId, projectId: ProjectId, cardId: BoardCardId, lines: List<String>): Int =
        authoredCards.tickChecklistItems(userId, projectId, cardId, lines)

    /** See [BoardAuthoredCards.rewordChecklistItem]. */
    @Transactional
    fun rewordChecklistItem(hire: HireBoard, cardId: BoardCardId, before: String, after: String): Boolean =
        authoredCards.rewordChecklistItem(hire, cardId, before, after)

    /** See [BoardAuthoredCards.editAuthoredCardForBuddy]. */
    @Transactional
    fun editAuthoredCardForBuddy(
        hire: HireBoard,
        cardId: BoardCardId,
        request: AuthoredCardRequest,
        basedOn: String?,
    ): BoardCardResponse = authoredCards.editAuthoredCardForBuddy(hire, cardId, request, basedOn)
}
