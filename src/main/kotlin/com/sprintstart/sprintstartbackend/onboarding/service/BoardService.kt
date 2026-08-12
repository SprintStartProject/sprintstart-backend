package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Board
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCardPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardDiagram
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ChecklistItemPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ChecklistPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.LinkPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.NotePayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.LinkCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.arrival.ArrivalStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ArrivalStepsContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardMomentKey
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardMomentResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardPullRequestResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardSuggestedTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardVocabularyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ChecklistItemResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.CompetencyProgressContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.CurrentTaskContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.LinkContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.MemoryRecapContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.NoteContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.OpenPullRequestsContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.PathToFirstContributionContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.SuggestedTasksContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardDiagramRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import kotlinx.serialization.json.Json
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
 * dismissed — then hydrates each surviving card with a live read. Relevance is re-evaluated on
 * every load, not only at creation.
 *
 * Each card's content is read from the same service the equivalent buddy tool reads. Nothing is
 * copied onto the card row.
 */
@Suppress("TooManyFunctions") // One hydration function per card kind, plus read/place/dismiss.
@Service
class BoardService(
    private val boardRepository: BoardRepository,
    private val boardCardRepository: BoardCardRepository,
    private val projectMembershipApi: ProjectMembershipApi,
    private val trackService: TrackService,
    private val onboardingMetricsService: OnboardingMetricsService,
    private val openPullRequestReader: OpenPullRequestReader,
    private val currentTaskReader: CurrentTaskReader,
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService,
    private val myCompetencyService: MyCompetencyService,
    private val buddySessionRepository: BuddySessionRepository,
    private val boardDiagramRepository: BoardDiagramRepository,
    private val boardDiagramService: BoardDiagramService,
    private val arrivalStepService: ArrivalStepService,
) {
    /**
     * This hire's board on this project, cards hydrated.
     *
     * @param userId The hire.
     * @param projectId The project the board belongs to.
     * @return The board, created on first read, or null when this hire is not a member of that
     * project — a board without a membership behind it has nothing to be about.
     */
    @Transactional
    fun getBoard(userId: UUID, projectId: UUID): BoardResponse? {
        val member = memberOrNull(userId, projectId) ?: return null
        val board = boardRepository.findByUserIdAndProjectId(userId, projectId)
            ?: boardRepository.save(Board(userId = userId, projectId = projectId))

        val track = trackService.forMember(member)
        // Read once and reuse: it decides whether the card is ensured at all, and then fills it.
        val arrivalSteps = arrivalStepService.forHire(userId)
        val cards = ensureRelevantCards(board, track, arrivalSteps.isNotEmpty())
        // Whether the hire is on a task at all, for the pin. Read through the same
        // [CurrentTaskReader] the card's content comes from, so the pin and the card agree.
        val onATask = currentTaskReader.currentTaskFor(userId, projectId) != null
        val timeline = onboardingMetricsService.getHireTimeline(userId, projectId)
        // One query for every diagram on the board, and the stored picture rather than a fresh one:
        // assembling costs a model call. The client revalidates afterwards.
        val diagrams = boardDiagramRepository
            .findAllByCardIdIn(cards.filter { it.kind == BoardCardKind.DIAGRAM }.map { it.id })
            .associateBy { it.cardId }

        return BoardResponse(
            boardId = board.id,
            projectId = projectId,
            vocabulary = BoardVocabularyResponse(
                trackLabel = track.label,
                contributionNoun = track.contributionNoun,
                contributionNounPlural = track.contributionNounPlural,
                contributionVerbPast = track.contributionVerbPast,
            ),
            cards = cards
                .filter { it.state == BoardCardState.ACTIVE }
                .sortedWith(attentionOrder(arrivalSteps, onATask))
                .map { it.toResponse(member, projectId, timeline, diagrams[it.id], arrivalSteps) },
        )
    }

    /**
     * The hire's own order, except that what needs them now comes first: outstanding arrival steps,
     * then the task they are on. Arrival outranks the current task.
     *
     * ⚠️ **A sort applied on read, never a write to `position`.** The hire's arrangement is
     * untouched underneath and returns exactly as they left it once the last step settles or the
     * task is done. Dismissal still wins over the pin: a dismissed card stays gone.
     *
     * ⚠️ **Nothing here caps or archives cards.** Removing a card automatically would break sticky
     * dismissal, which is the hire's alone.
     *
     * @param onATask Whether the hire actually has a task. A `CURRENT_TASK` card reading "nothing
     *   claimed yet" is not pinned.
     */
    private fun attentionOrder(
        arrivalSteps: List<ResolvedArrivalStep>,
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
     * ⚠️ **Every refusal returns as a sentence, never as silence.** It refuses:
     * - a kind this hire's track cannot support — see [supports];
     * - a card the hire dismissed, which is never put back;
     * - a card already there, left alone with its position;
     * - a [BoardCardKind.DIAGRAM] with no subject.
     *
     * @param userId The hire whose board it is.
     * @param projectId The project the board belongs to.
     * @param kind The card to place.
     * @param subject What a [BoardCardKind.DIAGRAM] is a diagram of. Required for that kind and
     *   ignored for every other.
     * @return What happened, in a form the caller can turn into a line for the model.
     */
    @Transactional
    fun place(
        userId: UUID,
        projectId: UUID,
        kind: BoardCardKind,
        subject: String? = null,
    ): PlacementOutcome {
        val member = memberOrNull(userId, projectId) ?: return PlacementOutcome.NOT_A_MEMBER
        if (!supports(trackService.forMember(member), kind)) return PlacementOutcome.UNSUPPORTED

        val cleanSubject = subject?.let { normaliseSubject(it) }?.takeIf { it.isNotBlank() }
        if (kind == BoardCardKind.DIAGRAM && cleanSubject == null) return PlacementOutcome.NEEDS_A_SUBJECT

        val board = boardRepository.findByUserIdAndProjectId(userId, projectId)
            ?: boardRepository.save(Board(userId = userId, projectId = projectId))
        val existing = boardCardRepository.findAllByBoardId(board.id)

        // For every kind but a diagram, one row per kind is the whole identity. A diagram is
        // identified by its *question* as well: two subjects are two different pictures, and
        // repurposing an existing card into a new subject would take away something the hire kept.
        existing.firstOrNull { it.kind == kind && it.matchesSubject(cleanSubject) }?.let { card ->
            return if (card.state == BoardCardState.DISMISSED) {
                PlacementOutcome.DISMISSED_BY_HIRE
            } else {
                PlacementOutcome.ALREADY_THERE
            }
        }

        boardCardRepository.save(
            BoardCard(
                boardId = board.id,
                kind = kind,
                owner = BoardCardOwner.AI,
                position = (existing.maxOfOrNull { it.position } ?: -1) + 1,
                // Dated, because the board says "your buddy put this here" only about cards it
                // actually did.
                placedAt = Instant.now(),
                subject = cleanSubject.takeIf { kind == BoardCardKind.DIAGRAM },
            ),
        )
        return PlacementOutcome.PLACED
    }

    /**
     * Whether this row is the same card as one of [kind] with [subject].
     *
     * ⚠️ Case- and whitespace-insensitive, so a dismissal sticks against a re-phrasing.
     */
    private fun BoardCard.matchesSubject(subject: String?): Boolean =
        kind != BoardCardKind.DIAGRAM ||
            this.subject?.let { normaliseSubject(it).equals(subject, ignoreCase = true) } == true

    private fun normaliseSubject(subject: String): String =
        subject.trim().replace(WHITESPACE, " ").take(MAX_SUBJECT_LENGTH)

    /**
     * Takes a card off the hire's board, for good.
     *
     * ⚠️ **The row survives with [BoardCardState.DISMISSED]; it is never deleted.** Both the
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
    fun dismiss(userId: UUID, cardId: UUID): Boolean {
        val card = boardCardRepository.findById(cardId).orElse(null) ?: return false
        val board = boardRepository.findById(card.boardId).orElse(null) ?: return false
        if (board.userId != userId) return false

        if (card.state != BoardCardState.DISMISSED) {
            card.state = BoardCardState.DISMISSED
            card.updatedAt = Instant.now()
            boardCardRepository.save(card)
        }
        return true
    }

    /**
     * Adds a card the hire wrote to their own board.
     *
     * Owned by the hire ([BoardCardOwner.HIRE]), which makes it theirs to edit and puts it out of
     * the mentor's reach. ⚠️ Several of these are allowed, unlike every other kind.
     *
     * @throws ResponseStatusException 404 when they are not a member of that project, 400 when the
     * content is empty.
     */
    @Transactional
    fun addAuthoredCard(userId: UUID, projectId: UUID, request: AuthoredCardRequest): BoardCardResponse {
        val member = memberOrNull(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project")
        val payload = request.toPayload()

        val board = boardRepository.findByUserIdAndProjectId(userId, projectId)
            ?: boardRepository.save(Board(userId = userId, projectId = projectId))
        val existing = boardCardRepository.findAllByBoardId(board.id)

        val card = boardCardRepository.save(
            BoardCard(
                boardId = board.id,
                kind = request.kind,
                owner = BoardCardOwner.HIRE,
                position = (existing.maxOfOrNull { it.position } ?: -1) + 1,
                payload = json.encodeToString(payload),
            ),
        )
        val arrivalSteps = arrivalStepService.forHire(member.userId)
        return card.toResponse(member, projectId, timeline = null, arrivalSteps = arrivalSteps)
    }

    /**
     * Replaces what one of the hire's own cards says. The payload is written whole, not patched.
     *
     * ⚠️ Ticking a checklist item comes through here too, which is why items carry ids: a tick is an
     * edit to that line, not to a position.
     *
     * @throws ResponseStatusException 404 when the card is not one of theirs, 400 when the content
     * is empty or the kind does not match the card being edited.
     */
    @Transactional
    fun editAuthoredCard(userId: UUID, cardId: UUID, request: AuthoredCardRequest): BoardCardResponse {
        val (card, board) = editableCardOrThrow(userId, cardId, request.kind)

        card.payload = json.encodeToString(request.toPayload())
        card.updatedAt = Instant.now()
        boardCardRepository.save(card)

        val member = memberOrNull(userId, board.projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You are not a member of that project")
        val arrivalSteps = arrivalStepService.forHire(member.userId)
        return card.toResponse(member, board.projectId, timeline = null, arrivalSteps = arrivalSteps)
    }

    /**
     * Puts the hire's cards in the order they asked for.
     *
     * Takes the whole order, not a from/to pair. Ids not on this board are ignored, not rejected.
     * ⚠️ Cards the request leaves out keep their relative order *after* the listed ones, so a client
     * that knows about only some of them cannot shuffle the rest.
     *
     * @throws ResponseStatusException 404 when they are not a member of that project.
     */
    @Transactional
    fun reorder(userId: UUID, projectId: UUID, cardIds: List<UUID>) {
        val board = boardRepository.findByUserIdAndProjectId(userId, projectId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "You have no board on that project")
        val cards = boardCardRepository.findAllByBoardId(board.id)
        val requested = cardIds.mapNotNull { id -> cards.firstOrNull { it.id == id } }
        val rest = cards.filterNot { card -> requested.any { it.id == card.id } }.sortedBy { it.position }

        val now = Instant.now()
        (requested + rest).forEachIndexed { index, card ->
            if (card.position != index) {
                card.position = index
                card.updatedAt = now
            }
        }
        boardCardRepository.saveAll(requested + rest)
    }

    /**
     * Which cards belong on [board] for this hire, creating any that are missing.
     *
     * Relevance is decided by the hire's track on *this* project, via the per-project
     * [TrackService.forMember] — not the permissive cross-project read the buddy's tool mounting
     * uses.
     *
     * ⚠️ A card whose kind already has a row is left exactly as it is, **dismissed rows included**.
     *
     * @return Every card row on the board, including ones the hire has dismissed.
     */
    private fun ensureRelevantCards(
        board: Board,
        track: OnboardingTrack,
        hasArrivalSteps: Boolean,
    ): List<BoardCard> {
        val existing = boardCardRepository.findAllByBoardId(board.id)
        val present = existing.map { it.kind }.toSet()
        val missing = relevantKinds(track, hasArrivalSteps).filterNot { it in present }
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
    private fun relevantKinds(track: OnboardingTrack, hasArrivalSteps: Boolean): List<BoardCardKind> =
        BoardCardKind.entries.filter {
            it.placement == BoardCardKind.Placement.BASELINE &&
                supports(track, it) &&
                // Not a track question, so not [supports]'s: an arrival card is meaningful for every
                // role and simply has nothing to say until somebody authors a step.
                (it != BoardCardKind.ARRIVAL_STEPS || hasArrivalSteps)
        }

    /**
     * Whether this hire's track could ever give this card something true to say.
     *
     * ⚠️ **The one hard gate on placement, binding the mentor as well as the baseline** — mirrors
     * where the equivalent buddy tool is mounted.
     */
    private fun supports(track: OnboardingTrack, kind: BoardCardKind): Boolean =
        kind != BoardCardKind.OPEN_PULL_REQUESTS ||
            track.admits(ContributionEvidenceKind.PULL_REQUEST)

    private fun hydrate(
        card: BoardCard,
        member: ProjectMember,
        projectId: UUID,
        timeline: HireTimelineResponse?,
        diagram: BoardDiagram?,
        arrivalSteps: List<ResolvedArrivalStep>,
    ): BoardCardContent = when (card.kind) {
        BoardCardKind.PATH_TO_FIRST_CONTRIBUTION -> pathContent(member, timeline)
        BoardCardKind.ARRIVAL_STEPS -> arrivalStepsContent(arrivalSteps)
        BoardCardKind.OPEN_PULL_REQUESTS -> openPullRequestsContent(member, projectId)
        BoardCardKind.CURRENT_TASK -> currentTaskContent(member.userId, projectId)
        BoardCardKind.SUGGESTED_TASKS -> suggestedTasksContent(member.userId, projectId)
        BoardCardKind.COMPETENCY_PROGRESS -> competencyProgressContent(member.userId)
        BoardCardKind.MEMORY_RECAP -> memoryRecapContent(member.userId)
        // The one card served from a cache: its content costs a model call.
        // [BoardDiagramService] owns whether that cache is still valid.
        BoardCardKind.DIAGRAM -> boardDiagramService.contentFor(card.subject.orEmpty(), diagram)
        BoardCardKind.NOTE, BoardCardKind.LINK, BoardCardKind.CHECKLIST -> authoredContent(card.payload)
    }

    /**
     * What is still outstanding before this hire can work, counted by how each step was settled.
     *
     * The same read the hire's own `GET /me/arrival` serves, so the card and that endpoint cannot
     * disagree — the rule every other card here follows.
     *
     * ⚠️ Counted per rigor and never totalled. A step the system observed and a step somebody
     * ticked are different facts, and a single blended figure here would be meaningless.
     */
    private fun arrivalStepsContent(steps: List<ResolvedArrivalStep>): ArrivalStepsContent {
        val responses: List<ArrivalStepResponse> = steps.map { it.toResponse() }

        return ArrivalStepsContent(
            steps = responses,
            observedCount = responses.count { it.rigor == Rigor.OBSERVED },
            declaredCount = responses.count { it.rigor == Rigor.DECLARED },
            outstandingCount = responses.count { !it.settled },
        )
    }

    /**
     * The hire's ledger, split at the bar rather than summed into a percentage.
     *
     * The same read and the same level-0 exclusion as the buddy's `get_my_competencies` tool.
     * ⚠️ **Level 0 means "asked, saw no evidence"** — a placement, not a competency — so it is
     * filtered out here. The ledger is global, not per project.
     */
    private fun competencyProgressContent(userId: UUID): CompetencyProgressContent {
        val (held, inProgress) = myCompetencyService
            .getCompetenciesForUser(userId)
            .filter { it.level > 0 }
            .partition { it.level >= it.targetLevel }
        return CompetencyProgressContent(
            held = held.map { it.toBoardResponse() },
            inProgress = inProgress.map { it.toBoardResponse() },
        )
    }

    private fun MyCompetencyResponse.toBoardResponse() = BoardCompetencyResponse(
        competencyKey = competencyKey,
        label = label,
        level = level,
        targetLevel = targetLevel,
    )

    /**
     * What the mentor remembers, read and never written.
     *
     * ⚠️ **Not [BuddyService.getOrCreateSession]** — hydrating a card must not create a session.
     */
    private fun memoryRecapContent(userId: UUID): MemoryRecapContent {
        val session = buddySessionRepository.findByUserId(userId)
        return MemoryRecapContent(
            memory = session?.summary,
            messagesRemembered = session?.summarizedCount ?: 0,
        )
    }

    /**
     * What the hire wrote, decoded.
     *
     * ⚠️ **A payload that cannot be decoded fails the whole board read; it is not swallowed into an
     * empty card.** A blank note reads as the board having lost the hire's work.
     */
    private fun authoredContent(payload: String?): BoardCardContent =
        when (val decoded = payload?.let { json.decodeFromString<BoardCardPayload>(it) }) {
            is NotePayload -> NoteContent(text = decoded.text)
            is LinkPayload -> LinkContent(url = decoded.url, label = decoded.label)
            is ChecklistPayload -> ChecklistContent(
                title = decoded.title,
                items = decoded.items.map {
                    ChecklistItemResponse(
                        id = UUID.fromString(it.id),
                        text = it.text,
                        done = it.done,
                    )
                },
            )
            // An authored card with no payload cannot happen: one is written when the card is
            // created and replaced when it is edited, never cleared.
            null -> error("Authored board card has no payload")
        }

    /**
     * The task the hire is on, read — never assigned.
     *
     * ⚠️ **Read through [CurrentTaskReader], not `TaskZeroService.getForHire`, which assigns on
     * read.** Hydration runs on every page load, so it must not be able to hand out a task.
     *
     * A card with no task on it is a real state and says so.
     */
    private fun currentTaskContent(userId: UUID, projectId: UUID): CurrentTaskContent {
        val task = currentTaskReader.currentTaskFor(userId, projectId)
        return CurrentTaskContent(
            taskId = task?.id,
            title = task?.title,
            summary = task?.summary,
            url = task?.sourceUrl,
            // True for a goal the hire claimed, false for a Task 0 they were handed.
            chosen = task != null && currentTaskReader.isClaimedGoal(userId, projectId),
        )
    }

    /**
     * Good next tasks, ranked. ⚠️ **Carries the reasons and never the score.**
     *
     * Same read and same cap as the buddy's `get_suggested_tasks` tool.
     */
    private fun suggestedTasksContent(userId: UUID, projectId: UUID): SuggestedTasksContent =
        SuggestedTasksContent(
            tasks = starterWorkTaskProposalService
                .matchForUserId(userId, projectId)
                .take(MAX_SUGGESTED_TASKS)
                .map { match ->
                    BoardSuggestedTaskResponse(
                        taskId = match.task.id,
                        title = match.task.title,
                        url = match.task.sourceUrl,
                        reasons = match.reasons,
                    )
                },
        )

    /**
     * The path card's content, from the same timeline the PM dashboard reads.
     *
     * A hire with no timeline at all still gets the card, with every moment unreached: "nothing has
     * happened yet" is the honest day-one state and is exactly what somebody on day one should see,
     * rather than a card that is missing until they have already made progress.
     */
    private fun pathContent(
        member: ProjectMember,
        timeline: HireTimelineResponse?,
    ): PathToFirstContributionContent = PathToFirstContributionContent(
        moments = listOf(
            // Joined comes from the membership rather than the timeline, so it is still shown when
            // there is no timeline to read.
            BoardMomentResponse(BoardMomentKey.JOINED, member.joinedAt),
            BoardMomentResponse(BoardMomentKey.TASK_CLAIMED, timeline?.firstTaskClaimedAt),
            // The timeline's field names still say "pull request"; the values behind them are
            // composed from contributions of any kind, which is why the card can name them
            // generally.
            BoardMomentResponse(BoardMomentKey.WORK_SUBMITTED, timeline?.firstContributionOpenedAt),
            BoardMomentResponse(BoardMomentKey.FIRST_RESPONSE, timeline?.firstResponseAt),
            BoardMomentResponse(BoardMomentKey.WORK_ACCEPTED, timeline?.firstContributionAcceptedAt),
        ),
        acceptedCount = timeline?.acceptedContributionCount ?: 0,
        autonomyReachedAt = timeline?.autonomyReachedAt,
        stalledReason = timeline?.stalledReason,
    )

    private fun openPullRequestsContent(
        member: ProjectMember,
        projectId: UUID,
    ): OpenPullRequestsContent {
        val login = member.githubLogin
        val open = openPullRequestReader.openFor(projectId, login)
        return OpenPullRequestsContent(
            pullRequests = open.map { pullRequest ->
                BoardPullRequestResponse(
                    artifactId = pullRequest.artifactId,
                    number = pullRequest.number,
                    title = pullRequest.title,
                    url = pullRequest.sourceUrl,
                    waitingHours = openPullRequestReader.waitingHours(pullRequest),
                )
            },
            attributionMissing = login.isNullOrBlank(),
        )
    }

    private fun BoardCard.toResponse(
        member: ProjectMember,
        projectId: UUID,
        timeline: HireTimelineResponse?,
        diagram: BoardDiagram? = null,
        arrivalSteps: List<ResolvedArrivalStep> = emptyList(),
    ) = BoardCardResponse(
        id = id,
        kind = kind,
        owner = owner,
        position = position,
        placedAt = placedAt,
        content = hydrate(this, member, projectId, timeline, diagram, arrivalSteps),
    )

    /**
     * The card this edit is allowed to change, with the board it sits on.
     *
     * ⚠️ **Three refusals, all answering 404**: a card that does not exist, one belonging to
     * somebody else, and a live card (which has no stored content to edit). A 403 would confirm the
     * id is somebody's real card.
     */
    private fun editableCardOrThrow(
        userId: UUID,
        cardId: UUID,
        kind: BoardCardKind,
    ): Pair<BoardCard, Board> {
        val card = boardCardRepository.findById(cardId).orElse(null)
        val board = card?.let { boardRepository.findById(it.boardId).orElse(null) }
        val refusal = when {
            card == null || board == null || board.userId != userId || card.owner != BoardCardOwner.HIRE ->
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
     * The request as something storable, rejecting content that would leave a card saying nothing.
     *
     * An empty note is not a note and a link with no address is not a link; keeping either would
     * leave a blank card on the board that nobody can explain later. A checklist with no items is
     * allowed — that is a list somebody is about to fill in, which is a real thing to make.
     */
    private fun AuthoredCardRequest.toPayload(): BoardCardPayload = when (this) {
        is NoteCardRequest -> NotePayload(text = text.requireContent("A note needs some text"))
        is LinkCardRequest -> LinkPayload(
            url = url.requireContent("A link needs an address"),
            label = label?.trim()?.ifBlank { null },
        )
        is ChecklistCardRequest -> ChecklistPayload(
            title = title?.trim()?.ifBlank { null },
            items = items
                // A blank line the hire never filled in is not an item; dropping it beats keeping a
                // tickable nothing.
                .filter { it.text.isNotBlank() }
                .map {
                    ChecklistItemPayload(
                        // A new item gets its id here rather than from the client, so two tabs
                        // adding a line cannot mint the same one.
                        id = (it.id ?: UUID.randomUUID()).toString(),
                        text = it.text.trim(),
                        done = it.done,
                    )
                },
        )
    }

    private fun String.requireContent(message: String): String =
        trim().ifBlank { throw ResponseStatusException(HttpStatus.BAD_REQUEST, message) }

    private fun memberOrNull(userId: UUID, projectId: UUID): ProjectMember? =
        projectMembershipApi.getProjectMembers(projectId).firstOrNull { it.userId == userId }

    /**
     * What placing a card did.
     *
     * Every outcome is reported rather than collapsed into a boolean, because the mentor has to say
     * something afterwards and "I've put that on your board" is only true for one of them. A buddy
     * that cannot tell a refusal from a success will claim the success.
     */
    enum class PlacementOutcome {
        PLACED,
        ALREADY_THERE,

        /** The hire took this card off their board before; it is not going back. */
        DISMISSED_BY_HIRE,

        /** This hire's track can never give the card anything true to say. */
        UNSUPPORTED,
        NOT_A_MEMBER,

        /** A diagram was asked for without saying what it should be a diagram of. */
        NEEDS_A_SUBJECT,
    }

    private companion object {
        /** Matches the buddy tool's cap, so the card and the conversation list the same tasks. */
        const val MAX_SUGGESTED_TASKS = 3

        /**
         * Long enough for any real question, short enough that a rambling one cannot become a card
         * title nobody can read. Matches the cap the AI service applies to the same string.
         */
        const val MAX_SUBJECT_LENGTH = 200

        val WHITESPACE = Regex("\\s+")

        /** Lenient on unknown keys so a payload written by a newer version still reads back. */
        val json = Json { ignoreUnknownKeys = true }
    }
}
