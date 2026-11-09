package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.arrival.external.ArrivalReadApi
import com.sprintstart.sprintstartbackend.arrival.model.response.arrival.ArrivalStepResponse
import com.sprintstart.sprintstartbackend.board.external.BoardCardVersion
import com.sprintstart.sprintstartbackend.board.external.BoardReading
import com.sprintstart.sprintstartbackend.board.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardChange
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.board.external.enums.BoardCardState
import com.sprintstart.sprintstartbackend.board.external.enums.PlacementOutcome
import com.sprintstart.sprintstartbackend.board.model.entity.Board
import com.sprintstart.sprintstartbackend.board.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.board.model.entity.BoardCardPayload
import com.sprintstart.sprintstartbackend.board.model.entity.ChecklistItemPayload
import com.sprintstart.sprintstartbackend.board.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.board.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.board.model.request.board.NoteCardRequest
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
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
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
        val member = memberOrNull(userId, projectId) ?: return null
        val board = boardOf(userId, projectId)

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
                    hydrator.render(it, member, projectId, diagrams[it.id], arrivalSteps, pathSteps) { matches }
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
    @Transactional
    fun place(
        userId: UserId,
        projectId: ProjectId,
        kind: BoardCardKind,
        subject: String? = null,
        by: BoardActor = BoardActor.BUDDY,
    ): PlacementOutcome {
        val member = memberOrNull(userId, projectId) ?: return PlacementOutcome.NOT_A_MEMBER

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

        val board = boardOf(userId, projectId)
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
    @Transactional
    fun placeOrRevive(
        userId: UserId,
        projectId: ProjectId,
        kind: BoardCardKind,
        by: BoardActor,
    ): PlacementOutcome {
        require(!kind.takesSubject) { "$kind is identified by its subject; revive it through place()" }
        val outcome = place(userId, projectId, kind, by = by)
        if (outcome != PlacementOutcome.DISMISSED_BY_HIRE) return outcome

        val board = existingBoardOf(userId, projectId) ?: return outcome
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
    @Transactional
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
    @Transactional
    fun dismissForBuddy(userId: UserId, projectId: ProjectId, cardIds: List<BoardCardId>): Int {
        val board = buddyBoardOrThrow(userId, projectId)
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
     * Adds a note, link or checklist to the hire's own board.
     *
     * Owned by the hire ([BoardCardOwner.HIRE]) whoever wrote it, which makes it theirs to edit and
     * to throw away. Several of these are allowed, unlike every other kind.
     *
     * [by] says who wrote it. One the buddy wrote — behind a confirm the hire pressed — is still
     * theirs, and is dated with [BoardCard.placedAt] as well, so the board says "your buddy put this
     * here" about exactly the cards it did.
     *
     * @throws ResponseStatusException 404 when they are not a member of that project, 400 when the
     * content is empty.
     */
    @Transactional
    fun addAuthoredCard(
        userId: UserId,
        projectId: ProjectId,
        request: AuthoredCardRequest,
        by: BoardActor = BoardActor.HIRE,
    ): BoardCardResponse {
        val member = memberOrNull(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project")
        val payload = request.toPayload()

        val board = boardOf(userId, projectId)
        val existing = boardCardRepository.findAllByBoardId(board.id)

        val now = Instant.now()
        val card = boardCardRepository.save(
            BoardCard(
                boardId = board.id,
                kind = request.kind,
                owner = BoardCardOwner.HIRE,
                position = (existing.maxOfOrNull { it.position } ?: -1) + 1,
                placedAt = now.takeIf { by == BoardActor.BUDDY },
                payload = json.encodeToString(payload),
            ).apply { recordChange(BoardCardChange.CREATED, by, now) },
        )
        return hydrator.render(card, member, projectId)
    }

    /**
     * Replaces what one of the hire's own cards says. The payload is written whole, not patched.
     *
     * Ticking a checklist item comes through here too, which is why items carry ids: a tick is an
     * edit to that line, not to a position.
     *
     * @throws ResponseStatusException 404 when the card is not one of theirs, 400 when the content
     * is empty or the kind does not match the card being edited.
     */
    @Transactional
    fun editAuthoredCard(userId: UserId, cardId: BoardCardId, request: AuthoredCardRequest): BoardCardResponse {
        val (card, board) = editableCardOrThrow(userId, cardId, request.kind)

        val before = card.payload
        card.payload = json.encodeToString(request.toPayload())
        card.recordChange(changeBetween(before, card.payload), BoardActor.HIRE)
        boardCardRepository.save(card)

        val projectId = ProjectId(board.projectId)
        val member = memberOrNull(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project")
        return hydrator.render(card, member, projectId)
    }

    /**
     * Adds lines to the end of one of the hire's checklists, and can do nothing else to it.
     *
     * **Append-only, enforced here rather than asked of the caller.** [editAuthoredCard] replaces a
     * card's content whole, which is right for the hire editing their own card and wrong for the
     * mentor adding to one: given the whole list to send back, a model that rewords a line it
     * dislikes, drops one it thinks is done, or reorders them into what it considers a better
     * sequence has silently edited the hire's card, and the hire has no way to see what changed.
     * So the existing items are read from storage and copied through untouched — their ids, their
     * words, their ticks — and the new lines can only land after them.
     *
     * Ids are minted here for the new lines, the same way [addAuthoredCard] mints them, so a tick
     * still lands on a line rather than on a position.
     *
     * @throws ResponseStatusException 404 when they are not a member of [projectId], or the card is
     * not an active checklist of theirs on that project's board; 400 when there is nothing to add.
     */
    @Transactional
    fun appendChecklistItems(
        userId: UserId,
        projectId: ProjectId,
        cardId: BoardCardId,
        lines: List<String>,
    ): BoardCardResponse {
        val (card, member) = buddyEditableCardOrThrow(userId, projectId, cardId, BoardCardKind.CHECKLIST)
        val existing = card.checklistOrThrow()
        val added = lines.filter { it.isNotBlank() }.ifEmpty {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "There were no lines to add")
        }

        card.rewriteForBuddy(
            existing.copy(
                items = existing.items +
                    added.map { ChecklistItemPayload(id = UUID.randomUUID().toString(), text = it) },
            ),
        )

        return hydrator.render(card, member, projectId)
    }

    /**
     * Ticks lines the hire says they have done, and can do nothing else to the card.
     *
     * Matched by their **words**, not by an id, for the reason marks are (`marks/cardMarks.ts`):
     * making this work by id would mean putting every item's id in the mentor's prompt, and the
     * mentor would then be one slip away from reading one out. A line the text does not match is
     * simply not ticked, and the caller is told how many were — silence would let a typo look like
     * success.
     *
     * **It only ever sets done, never clears it.** Un-ticking is the hire saying they were wrong
     * about their own work, which is not something anybody should be able to do on their behalf;
     * the checkbox on the card is right there. Nothing else moves either: no text changes, no
     * re-ordering, no lines added or dropped.
     *
     * @throws ResponseStatusException 404 when they are not a member of [projectId], or the card is
     * not an active checklist of theirs on that project's board.
     */
    @Transactional
    fun tickChecklistItems(userId: UserId, projectId: ProjectId, cardId: BoardCardId, lines: List<String>): Int {
        val (card, _) = buddyEditableCardOrThrow(userId, projectId, cardId, BoardCardKind.CHECKLIST)
        val existing = card.checklistOrThrow()
        val wanted = lines.map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

        var ticked = 0
        val items = existing.items.map { item ->
            if (!item.done && item.text.trim().lowercase() in wanted) {
                ticked++
                item.copy(done = true)
            } else {
                item
            }
        }
        if (ticked == 0) return 0

        card.rewriteForBuddy(existing.copy(items = items), BoardCardChange.TICKED)

        return ticked
    }

    /**
     * Rewrites one line of a checklist, keeping everything the line is apart from its words.
     *
     * Its id survives, so a tick stays on the line rather than sliding to a neighbour, and so does
     * whether it was ticked — rewording a step is not undoing it. Nothing else on the card moves.
     *
     * **Refuses an ambiguous match rather than picking one.** Two lines that read the same are rare
     * and a card where the wrong one silently changed is worse than a card that did not change: the
     * hire asked for one edit and would have to diff the list to find out they got another.
     *
     * @return true when a line was rewritten, false when the text matched none or more than one.
     * @throws ResponseStatusException 404 when they are not a member of [projectId], or the card is
     * not an active checklist of theirs on that project's board.
     */
    @Transactional
    fun rewordChecklistItem(
        userId: UserId,
        projectId: ProjectId,
        cardId: BoardCardId,
        before: String,
        after: String,
    ): Boolean {
        val (card, _) = buddyEditableCardOrThrow(userId, projectId, cardId, BoardCardKind.CHECKLIST)
        val existing = card.checklistOrThrow()
        val wanted = before.trim().lowercase()
        val words = after.trim()
        if (words.isEmpty()) return false

        val matches = existing.items.filter { it.text.trim().lowercase() == wanted }
        if (matches.size != 1) return false

        card.rewriteForBuddy(
            existing.copy(
                items = existing.items.map { item ->
                    if (item.id == matches.first().id) item.copy(text = words) else item
                },
            ),
        )

        return true
    }

    /**
     * Replaces what one of the hire's notes, links or checklists says, on the buddy's behalf, once
     * the hire has confirmed the new content.
     *
     * Written whole, like the hire's own [editAuthoredCard] — the confirm showed the whole card, so
     * the whole card is what lands. A checklist keeps what its lines *are* apart from their words:
     * a line whose text survives the edit keeps its id and its tick, so tidying a list does not
     * silently undo the work already ticked off it, and a tick still lands on a line rather than a
     * position. A line the edit drops is gone; a new one arrives unticked.
     *
     * @param basedOn The fingerprint ([BoardCardVersion]) of the card as the proposal read it.
     * @throws ResponseStatusException 404 when they are not a member of [projectId], or the card is
     * not an active card of that kind on that project's board; 400 when the content is empty; 409
     * when the card's words are not those [basedOn] names, or it is a note longer than the buddy can
     * read, so that replacing it whole would lose what the proposal never showed.
     */
    @Transactional
    fun editAuthoredCardForBuddy(
        userId: UserId,
        projectId: ProjectId,
        cardId: BoardCardId,
        request: AuthoredCardRequest,
        basedOn: String?,
    ): BoardCardResponse {
        val (card, member) = buddyEditableCardOrThrow(userId, projectId, cardId, request.kind)
        // Decided on the row this transaction holds the lock on, so nothing can change it between
        // this check and the replace below. A missing fingerprint cannot vouch for anything.
        if (basedOn == null || basedOn != BoardCardVersion.of(card.decodedPayload())) {
            throw ResponseStatusException(HttpStatus.CONFLICT, BoardCardVersion.CARD_CHANGED)
        }
        if (request is NoteCardRequest && card.noteLength() > BoardReading.NOTE_PREVIEW) {
            throw ResponseStatusException(HttpStatus.CONFLICT, BoardCardVersion.NOTE_TOO_LONG_TO_REPLACE)
        }
        val content = if (request is ChecklistCardRequest) {
            request.keepingLinesOf(card.checklistOrThrow())
        } else {
            request
        }

        card.rewriteForBuddy(content.toPayload())

        return hydrator.render(card, member, projectId)
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
    @Transactional
    fun reorder(userId: UserId, projectId: ProjectId, cardIds: List<BoardCardId>) {
        val board = existingBoardOf(userId, projectId)
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
    @Transactional
    fun reorderForBuddy(userId: UserId, projectId: ProjectId, cardIds: List<BoardCardId>): Int =
        reorderOn(buddyBoardOrThrow(userId, projectId), cardIds, BoardActor.BUDDY)

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

    /**
     * The card this edit is allowed to change, with the board it sits on.
     *
     * Three refusals, all answering 404: a card that does not exist, one belonging to
     * somebody else, and a live card (which has no stored content to edit). A 403 would confirm the
     * id is somebody's real card.
     */
    private fun editableCardOrThrow(
        userId: UserId,
        cardId: BoardCardId,
        kind: BoardCardKind,
    ): Pair<BoardCard, Board> {
        // Locked, so a hire edit and a buddy edit of the same card cannot interleave their writes.
        val card = boardCardRepository.findLockedById(cardId.value)
        val board = card?.let { boardRepository.findById(it.boardId).orElse(null) }
        val refusal = when {
            card == null || board == null || board.userId != userId.value || card.owner != BoardCardOwner.HIRE ->
                ResponseStatusException(HttpStatus.NOT_FOUND, "No such card on your board")
            card.kind != kind ->
                ResponseStatusException(HttpStatus.BAD_REQUEST, "That card is a ${card.kind}, not a $kind")
            else -> null
        }
        if (refusal != null || card == null || board == null) {
            throw refusal ?: ResponseStatusException(HttpStatus.NOT_FOUND, "No such card on your board")
        }
        return card to board
    }

    /**
     * The card a buddy edit may change, locked until the edit commits.
     *
     * **This is where the buddy is let into the hire's own cards, and the only place.** The rule
     * used to be that a card the hire wrote was out of the mentor's reach entirely. It is now that
     * the buddy may do to the hire's board what the hire can — create, edit, tick, dismiss, move —
     * provided every change is one the hire confirmed and every change is attributed to the buddy
     * ([BoardCard.recordChange]). Pre-approval and attribution replace the old exclusion; nothing
     * replaces them. So the widening lives here rather than as a special case in each write: the
     * owner check below still admits only [BoardCardOwner.HIRE] cards — a live card has no stored
     * content for anybody to edit — and every buddy write to a card's content comes through this.
     *
     * Stricter than [editableCardOrThrow] on purpose. The hire's own edit comes from a board they
     * are looking at; a buddy edit is confirmed from a proposal that may be stale — made on a
     * project they have since left, or before they took the card off their board. So it must be
     * a member of [projectId] **now**, on that project's board, and the card must still be an
     * active card of theirs, of the kind the proposal said.
     *
     * Membership is checked before anything is read, so a hire who has left the project is
     * refused before a write can happen, not after one already has. The card is read through the
     * lock, which is what makes the read-change-write in each caller atomic.
     *
     * One refusal for every way the card can be wrong, the same as [editableCardOrThrow]: whether
     * a card exists on somebody else's board is not this caller's business.
     *
     * @throws ResponseStatusException 404 in every case.
     */
    private fun buddyEditableCardOrThrow(
        userId: UserId,
        projectId: ProjectId,
        cardId: BoardCardId,
        kind: BoardCardKind,
    ): Pair<BoardCard, ProjectMember> {
        val member = memberOrNull(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project")
        val card = existingBoardOf(userId, projectId)
            ?.let { board -> boardCardRepository.findLockedById(cardId.value)?.takeIf { it.boardId == board.id } }
            ?.takeIf { it.owner == BoardCardOwner.HIRE && it.state == BoardCardState.ACTIVE }
            ?.takeIf { it.kind == kind }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No such ${kind.name.lowercase()} on your board")
        return card to member
    }

    /**
     * The board a buddy write to the arrangement — a dismissal, an order — lands on.
     *
     * The same staleness rule as [buddyEditableCardOrThrow]: a member of [projectId] now, checked
     * before anything is read, and only ever that project's board.
     *
     * @throws ResponseStatusException 404 when they are not a member, or have no board there.
     */
    private fun buddyBoardOrThrow(userId: UserId, projectId: ProjectId): Board {
        memberOrNull(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project")
        return existingBoardOf(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You have no board on that project")
    }

    private fun BoardCard.rewriteForBuddy(content: BoardCardPayload, change: BoardCardChange = BoardCardChange.EDITED) {
        payload = json.encodeToString(content)
        recordChange(change, BoardActor.BUDDY)
        boardCardRepository.save(this)
    }

    private fun memberOrNull(userId: UserId, projectId: ProjectId): ProjectMember? =
        projectMembershipApi.getProjectMembers(projectId.value).firstOrNull { it.userId == userId.value }

    private fun existingBoardOf(userId: UserId, projectId: ProjectId): Board? =
        boardRepository.findByUserIdAndProjectId(userId.value, projectId.value)

    /** The hire's board on [projectId], created empty the first time anything needs it. */
    private fun boardOf(userId: UserId, projectId: ProjectId): Board =
        existingBoardOf(userId, projectId)
            ?: boardRepository.save(Board(userId = userId.value, projectId = projectId.value))

    private companion object {
        /**
         * Long enough for any real question, short enough that a rambling one cannot become a card
         * title nobody can read. Matches the cap the AI service applies to the same string.
         */
        const val MAX_SUBJECT_LENGTH = 200

        val WHITESPACE = Regex("\\s+")

        /** The same lenient codec [authoredContent] and the rest of `AuthoredCardPayloads.kt` read with. */
        val json = boardPayloadJson
    }
}
