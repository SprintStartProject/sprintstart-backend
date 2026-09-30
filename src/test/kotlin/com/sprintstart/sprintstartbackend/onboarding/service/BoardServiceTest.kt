package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardActor
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardChange
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.external.enums.TaskType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Board
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCardPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ChecklistItemPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ChecklistPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.NotePayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPath
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingPhase
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingResource
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTask
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistItemRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.LinkCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ArrivalStepsContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardMomentKey
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardMomentResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.CompetencyProgressContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.CurrentTaskContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.MemoryRecapContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.OpenPullRequestsContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.PathStepContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.PathToFirstContributionContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.SuggestedTasksContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.TaskPoolContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.RankedStarterWorkTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardDiagramRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.OnboardingPathRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tracks [BoardService]'s own surface, which is why it is long: card mounting, placement rules,
 * dismissal, ordering, hydration and the mentor's refusals all belong to one service and one
 * shared fixture. Splitting by concern would duplicate that fixture rather than separate anything,
 * so the size is suppressed here for the same reason [BoardService] suppresses `TooManyFunctions`.
 */
@Suppress("LargeClass")
class BoardServiceTest {
    private val boardRepository: BoardRepository = mockk()
    private val boardCardRepository: BoardCardRepository = mockk()
    private val projectMembershipApi: ProjectMembershipApi = mockk()
    private val onboardingMetricsService: OnboardingMetricsService = mockk()
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()
    private val currentTaskReader: CurrentTaskReader = mockk()
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService = mockk()
    private val myCompetencyService: MyCompetencyService = mockk()
    private val buddySessionRepository: BuddySessionRepository = mockk()
    private val boardDiagramRepository: BoardDiagramRepository = mockk()
    private val onboardingPathRepository: OnboardingPathRepository = mockk()
    private val onboardingTaskService: OnboardingTaskService = mockk()

    // Relaxed, and empty by default: arrival steps are incidental to these tests, and an
    // empty list means no arrival card is ensured, so every card assertion here is unaffected.
    private val arrivalStepService: ArrivalStepService = mockk(relaxed = true)
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)

    private val pathStepReader = PathStepReader(onboardingPathRepository)

    private val json = Json { ignoreUnknownKeys = true }

    private val boardDiagramService = BoardDiagramService(
        boardRepository,
        boardCardRepository,
        boardDiagramRepository,
        onboardingAiClient,
        transactionManager,
    )

    private val now: Instant = Instant.parse("2026-07-27T12:00:00Z")
    private val hireId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    private val service = BoardService(
        boardRepository,
        boardCardRepository,
        projectMembershipApi,
        onboardingMetricsService,
        OpenPullRequestReader(artifactIngestionApi, Clock.fixed(now, ZoneOffset.UTC)),
        currentTaskReader,
        starterWorkTaskProposalService,
        myCompetencyService,
        buddySessionRepository,
        boardDiagramRepository,
        boardDiagramService,
        arrivalStepService,
        pathStepReader,
        onboardingTaskService,
    )

    @BeforeEach
    fun setUp() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member())
        every { onboardingMetricsService.getHireTimeline(hireId, projectId) } returns timeline()
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "ada") } returns emptyList()
        every { boardRepository.save(any()) } answers { firstArg() }
        every { boardCardRepository.saveAll(any<List<BoardCard>>()) } answers { firstArg() }
        every { boardCardRepository.findAllByBoardId(any()) } returns emptyList()
        every { boardCardRepository.save(any()) } answers { firstArg() }
        every { currentTaskReader.currentTaskFor(hireId, projectId) } returns null
        every { currentTaskReader.isClaimedGoal(hireId, projectId) } returns false
        every { starterWorkTaskProposalService.matchForUserId(hireId, projectId) } returns emptyList()
        every { myCompetencyService.getCompetenciesForUser(hireId) } returns emptyList()
        every { buddySessionRepository.findByUserId(hireId) } returns null
        every { boardDiagramRepository.findAllByCardIdIn(any()) } returns emptyList()
        every { onboardingPathRepository.findOnboardingPathByUserId(hireId) } returns Optional.empty()
    }

    private fun member(
        githubLogin: String? = "ada",
        joinedAt: Instant? = now.minusSeconds(86_400),
    ) = ProjectMember(
        userId = hireId,
        displayName = "Ada",
        githubLogin = githubLogin,
        joinedAt = joinedAt,
    )

    @Suppress("LongParameterList")
    private fun timeline(
        firstTaskClaimedAt: Instant? = null,
        firstOpenedAt: Instant? = null,
        firstResponseAt: Instant? = null,
        acceptedAt: Instant? = null,
        acceptedCount: Int = 0,
        stalledReason: String? = null,
        autonomyReachedAt: Instant? = null,
    ) = HireTimelineResponse(
        userId = hireId,
        displayName = "Ada",
        githubLogin = "ada",
        joinedAt = now.minusSeconds(86_400),
        taskZeroAssignedAt = null,
        firstTaskClaimedAt = firstTaskClaimedAt,
        firstContributionOpenedAt = firstOpenedAt,
        firstResponseAt = firstResponseAt,
        firstContributionAcceptedAt = acceptedAt,
        hoursToFirstAcceptedContribution = null,
        hoursToFirstResponse = null,
        acceptedContributionCount = acceptedCount,
        openContributionCount = 0,
        longestOpenWaitHours = null,
        stalled = stalledReason != null,
        stalledReason = stalledReason,
        autonomyReachedAt = autonomyReachedAt,
        returnedContributionCount = 0,
    )

    private fun existingBoard(): Board {
        val board = Board(userId = hireId, projectId = projectId)
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns board
        return board
    }

    private fun noBoardYet() {
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns null
    }

    @Test
    fun `creates the board on first read`() {
        noBoardYet()

        val board = service.getBoard(hireId, projectId)

        assertNotNull(board)
        verify { boardRepository.save(any()) }
    }

    @Test
    fun `a hire who is not a member of the project has no board`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        assertNull(service.getBoard(hireId, projectId))
    }

    @Test
    fun `an engineering hire gets the path, open pull request and task pool cards`() {
        noBoardYet()

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        assertEquals(
            listOf(
                BoardCardKind.PATH_TO_FIRST_CONTRIBUTION,
                BoardCardKind.OPEN_PULL_REQUESTS,
                BoardCardKind.TASK_POOL,
            ),
            kinds,
        )
    }

    @Test
    fun `no arrival card is mounted while nobody has authored a step`() {
        noBoardYet()
        every { arrivalStepService.forHire(hireId) } returns emptyList()

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        // "Absent, never empty" — the rule the pull-request card already follows. A card that
        // permanently reads "nothing to do" is worse than no card, and an installation where
        // nobody has written an arrival list is the normal state until somebody does.
        assertFalse(kinds!!.contains(BoardCardKind.ARRIVAL_STEPS))
    }

    @Test
    fun `the arrival card is mounted once a step applies, and counts per rigor`() {
        noBoardYet()
        every { arrivalStepService.forHire(hireId) } returns listOf(
            ResolvedArrivalStep(
                step = ArrivalStep(key = "github-account", title = "Create a GitHub account"),
                settledAt = now,
                rigor = Rigor.DECLARED,
            ),
            ResolvedArrivalStep(
                step = ArrivalStep(key = "vpn", title = "Request VPN access"),
                settledAt = null,
                rigor = null,
            ),
        )

        val content = service
            .getBoard(hireId, projectId)
            ?.cards
            ?.first { it.kind == BoardCardKind.ARRIVAL_STEPS }
            ?.content as ArrivalStepsContent

        assertEquals(2, content.steps.size)
        assertEquals(1, content.declaredCount)
        assertEquals(0, content.observedCount)
        assertEquals(1, content.outstandingCount)
    }

    /**
     * Attention ordering: a hire who cannot clone the repository should not have to scroll to find
     * out what to do about it. The arrival card is ensured *after* the others, so without this it
     * lands last — the worst possible position for the most urgent thing.
     */
    @Test
    fun `an outstanding arrival step puts its card first`() {
        noBoardYet()
        every { arrivalStepService.forHire(hireId) } returns listOf(
            ResolvedArrivalStep(
                step = ArrivalStep(key = "vpn", title = "Request VPN access"),
                settledAt = null,
                rigor = null,
            ),
        )

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        assertEquals(BoardCardKind.ARRIVAL_STEPS, kinds!!.first())
    }

    /**
     * The override is narrow on purpose: it lasts exactly as long as something is outstanding. Once
     * everything settles the card drops back to where the hire's own ordering put it — which is
     * what makes overriding that ordering acceptable rather than destructive.
     */
    @Test
    fun `a fully settled arrival card takes its ordinary place again`() {
        noBoardYet()
        every { arrivalStepService.forHire(hireId) } returns listOf(
            ResolvedArrivalStep(
                step = ArrivalStep(key = "vpn", title = "Request VPN access"),
                settledAt = now,
                rigor = Rigor.DECLARED,
            ),
        )

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        assertEquals(BoardCardKind.PATH_TO_FIRST_CONTRIBUTION, kinds!!.first())
        assertTrue(kinds.contains(BoardCardKind.ARRIVAL_STEPS))
    }

    /**
     * Without a pin, the task somebody is on has no primacy over any other card, so on a board of
     * any size it is findable only by looking.
     */
    @Test
    fun `the task the hire is on comes first`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.PATH_TO_FIRST_CONTRIBUTION, position = 0),
            card(board, BoardCardKind.OPEN_PULL_REQUESTS, position = 1),
            card(board, BoardCardKind.CURRENT_TASK, position = 2),
        )
        every { currentTaskReader.currentTaskFor(hireId, projectId) } returns StarterWorkTaskProposal(
            sourceId = "github:org/repo:ISSUE:7",
            title = "Fix the flaky login test",
        )

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        assertEquals(BoardCardKind.CURRENT_TASK, kinds!!.first())
        // Underneath, their own arrangement is untouched -- the pin is a sort on read, never a
        // write to `position`, which is what makes overriding it acceptable rather than destructive.
        assertEquals(
            listOf(
                BoardCardKind.PATH_TO_FIRST_CONTRIBUTION,
                BoardCardKind.OPEN_PULL_REQUESTS,
                BoardCardKind.TASK_POOL,
            ),
            kinds.drop(1),
        )
    }

    /**
     * The pin lasts exactly as long as the thing it is about is true. A hire between tasks gets
     * their own order back, which is the same narrowness the arrival pin has.
     */
    @Test
    fun `a hire on no task gets their own order back`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.PATH_TO_FIRST_CONTRIBUTION, position = 0),
            card(board, BoardCardKind.OPEN_PULL_REQUESTS, position = 1),
            card(board, BoardCardKind.CURRENT_TASK, position = 2),
        )

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        // The card is still there and still honest -- "nothing claimed yet" is a real state, and
        // a card that vanished would read as the board losing things. It simply does not get the
        // best place on the board for having nothing on it.
        assertEquals(
            listOf(
                BoardCardKind.PATH_TO_FIRST_CONTRIBUTION,
                BoardCardKind.OPEN_PULL_REQUESTS,
                BoardCardKind.CURRENT_TASK,
                BoardCardKind.TASK_POOL,
            ),
            kinds,
        )
    }

    /**
     * Arrival outranks the current task: what has to be true before somebody can work
     * comes before what they are working on. Somebody
     * still waiting on access does not need their task moved up, they need the access — and this is
     * the one case where the two pins compete.
     */
    @Test
    fun `an outstanding arrival step outranks the task the hire is on`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.CURRENT_TASK, position = 0),
            card(board, BoardCardKind.ARRIVAL_STEPS, position = 1),
        )
        every { arrivalStepService.forHire(hireId) } returns listOf(
            ResolvedArrivalStep(
                step = ArrivalStep(key = "vpn", title = "Request VPN access"),
                settledAt = null,
                rigor = null,
            ),
        )
        every { currentTaskReader.currentTaskFor(hireId, projectId) } returns StarterWorkTaskProposal(
            sourceId = "github:org/repo:ISSUE:7",
            title = "Fix the flaky login test",
        )

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        // Only the two pinned places are asserted: the baseline cards follow underneath in their
        // own order, which is not what this test is about.
        assertEquals(
            listOf(BoardCardKind.ARRIVAL_STEPS, BoardCardKind.CURRENT_TASK),
            kinds!!.take(2),
        )
    }

    /**
     * Crowding gets nothing, and this pins that. Every auto-tidy that *removes* a card breaks
     * the rule that dismissal is the hire's and is sticky — which rules out caps and archiving
     * outright, not merely for now. A board of many cards returns every one of them.
     */
    @Test
    fun `a busy board loses nothing to the ordering`() {
        val board = existingBoard()
        val kindsOnBoard = listOf(
            BoardCardKind.PATH_TO_FIRST_CONTRIBUTION,
            BoardCardKind.OPEN_PULL_REQUESTS,
            BoardCardKind.CURRENT_TASK,
            BoardCardKind.SUGGESTED_TASKS,
            BoardCardKind.COMPETENCY_PROGRESS,
            BoardCardKind.MEMORY_RECAP,
            BoardCardKind.TASK_POOL,
        )
        every { boardCardRepository.findAllByBoardId(board.id) } returns kindsOnBoard
            .mapIndexed { index, kind -> card(board, kind, position = index) }
        every { currentTaskReader.currentTaskFor(hireId, projectId) } returns StarterWorkTaskProposal(
            sourceId = "github:org/repo:ISSUE:7",
            title = "Fix the flaky login test",
        )

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        assertEquals(kindsOnBoard.size, kinds!!.size)
        assertEquals(kindsOnBoard.toSet(), kinds.toSet())
    }

    @Test
    fun `the path card is placed for every track, because its moments are not about git`() {
        noBoardYet()

        val board = service.getBoard(hireId, projectId)

        assertTrue(
            board?.cards.orEmpty().any { it.kind == BoardCardKind.PATH_TO_FIRST_CONTRIBUTION },
        )
    }

    @Test
    fun `ensuring cards is idempotent — a second read adds nothing`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            BoardCard(
                boardId = board.id,
                kind = BoardCardKind.PATH_TO_FIRST_CONTRIBUTION,
                owner = BoardCardOwner.AI,
                position = 0,
            ),
            BoardCard(
                boardId = board.id,
                kind = BoardCardKind.OPEN_PULL_REQUESTS,
                owner = BoardCardOwner.AI,
                position = 1,
            ),
            BoardCard(
                boardId = board.id,
                kind = BoardCardKind.TASK_POOL,
                owner = BoardCardOwner.AI,
                position = 2,
            ),
        )

        service.getBoard(hireId, projectId)

        verify(exactly = 0) { boardCardRepository.saveAll(any<List<BoardCard>>()) }
    }

    @Test
    fun `a dismissed card is not put back, and is not shown`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            BoardCard(
                boardId = board.id,
                kind = BoardCardKind.OPEN_PULL_REQUESTS,
                owner = BoardCardOwner.AI,
                state = BoardCardState.DISMISSED,
                position = 0,
            ),
        )

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        // The dismissed row is what makes the removal stick: the path card is added because it is
        // missing, the pull-request card is not re-added because the hire said no to it.
        assertEquals(listOf(BoardCardKind.PATH_TO_FIRST_CONTRIBUTION, BoardCardKind.TASK_POOL), kinds)
    }

    @Test
    fun `a newly relevant card is added after the cards already on the board`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            BoardCard(
                boardId = board.id,
                kind = BoardCardKind.PATH_TO_FIRST_CONTRIBUTION,
                owner = BoardCardOwner.AI,
                position = 7,
            ),
        )

        val cards = service.getBoard(hireId, projectId)?.cards.orEmpty()

        // Ensuring a card exists must never reshuffle a board the hire has arranged.
        assertEquals(
            listOf(BoardCardKind.OPEN_PULL_REQUESTS to 8, BoardCardKind.TASK_POOL to 9),
            cards.drop(1).map { it.kind to it.position },
        )
    }

    @Test
    fun `an unreached moment is absent, never zero`() {
        noBoardYet()

        val content = service.pathCard()

        assertEquals(now.minusSeconds(86_400), content.moments.momentAt(BoardMomentKey.JOINED))
        assertNull(content.moments.momentAt(BoardMomentKey.WORK_ACCEPTED))
        assertEquals(0, content.acceptedCount)
    }

    @Test
    fun `the path card reports the moments the timeline has reached`() {
        noBoardYet()
        val accepted = now.minusSeconds(3_600)
        every { onboardingMetricsService.getHireTimeline(hireId, projectId) } returns timeline(
            firstTaskClaimedAt = now.minusSeconds(50_000),
            firstOpenedAt = now.minusSeconds(20_000),
            firstResponseAt = now.minusSeconds(10_000),
            acceptedAt = accepted,
            acceptedCount = 2,
            autonomyReachedAt = accepted,
        )

        val content = service.pathCard()

        assertEquals(accepted, content.moments.momentAt(BoardMomentKey.WORK_ACCEPTED))
        assertEquals(2, content.acceptedCount)
        assertEquals(accepted, content.autonomyReachedAt)
    }

    @Test
    fun `a hire with no timeline still gets the card, with nothing reached`() {
        noBoardYet()
        every { onboardingMetricsService.getHireTimeline(hireId, projectId) } returns null

        val content = service.pathCard()

        // Day one is a real state, and the card that describes it must exist on day one.
        assertEquals(now.minusSeconds(86_400), content.moments.momentAt(BoardMomentKey.JOINED))
        assertTrue(content.moments.drop(1).all { it.reachedAt == null })
    }

    @Test
    fun `a stall is shown to the person in it`() {
        noBoardYet()
        every { onboardingMetricsService.getHireTimeline(hireId, projectId) } returns
            timeline(stalledReason = "no response in 5 days")

        assertEquals("no response in 5 days", service.pathCard().stalledReason)
    }

    @Test
    fun `open pull requests are listed longest-waiting first, with the answered one not waiting`() {
        noBoardYet()
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "ada") } returns listOf(
            openPullRequest(number = 2, openedAt = now.minusSeconds(3_600)),
            openPullRequest(
                number = 3,
                openedAt = now.minusSeconds(360_000),
                firstResponseAt = now.minusSeconds(1_000),
            ),
            openPullRequest(number = 1, openedAt = now.minusSeconds(72_000)),
        )

        val content = service.pullRequestCard()

        assertEquals(listOf(3, 1, 2), content.pullRequests.map { it.number })
        // Answered: the clock the hire cares about has stopped, so it is not "waiting" at all.
        assertNull(content.pullRequests.first { it.number == 3 }.waitingHours)
        assertEquals(20, content.pullRequests.first { it.number == 1 }.waitingHours)
        assertFalse(content.attributionMissing)
    }

    @Test
    fun `a pull request closed without merging is not open`() {
        noBoardYet()
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "ada") } returns listOf(
            openPullRequest(number = 4, openedAt = now.minusSeconds(3_600), state = "CLOSED"),
        )

        assertTrue(service.pullRequestCard().pullRequests.isEmpty())
    }

    @Test
    fun `no declared GitHub login reads as unattributable, not as nothing open`() {
        noBoardYet()
        every { projectMembershipApi.getProjectMembers(projectId) } returns
            listOf(member(githubLogin = null))

        val content = service.pullRequestCard()

        assertTrue(content.pullRequests.isEmpty())
        assertTrue(content.attributionMissing)
    }

    private fun openPullRequest(
        number: Int,
        openedAt: Instant,
        firstResponseAt: Instant? = null,
        state: String? = "OPEN",
    ) = AuthoredPullRequest(
        artifactId = UUID.randomUUID(),
        openedAt = openedAt,
        firstResponseAt = firstResponseAt,
        mergedAt = null,
        state = state,
        number = number,
        title = "PR $number",
        sourceUrl = "https://example.test/pr/$number",
    )

    // ---- the mentor places (slice 1) ----

    @Test
    fun `placing a card the board does not keep itself adds it, dated`() {
        val board = existingBoard()
        val saved = slot<BoardCard>()
        every { boardCardRepository.save(capture(saved)) } answers { firstArg() }

        val outcome = service.place(hireId, projectId, BoardCardKind.SUGGESTED_TASKS)

        assertEquals(BoardService.PlacementOutcome.PLACED, outcome)
        assertEquals(board.id, saved.captured.boardId)
        // Dated, because the board claims "your buddy added this" only about cards it actually did.
        assertNotNull(saved.captured.placedAt)
    }

    @Test
    fun `a card the hire dismissed is never put back by the mentor`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.SUGGESTED_TASKS, state = BoardCardState.DISMISSED),
        )

        // Sticky removal has to bind the mentor too, or dismissing is a gesture the next
        // conversation undoes.
        assertEquals(
            BoardService.PlacementOutcome.DISMISSED_BY_HIRE,
            service.place(hireId, projectId, BoardCardKind.SUGGESTED_TASKS),
        )
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    @Test
    fun `placing a card that is already there changes nothing`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.SUGGESTED_TASKS, position = 3),
        )

        // Re-placing would let the mentor reshuffle a board the hire has arranged.
        assertEquals(
            BoardService.PlacementOutcome.ALREADY_THERE,
            service.place(hireId, projectId, BoardCardKind.SUGGESTED_TASKS),
        )
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    @Test
    fun `placing for a project the hire is not on does nothing`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        assertEquals(
            BoardService.PlacementOutcome.NOT_A_MEMBER,
            service.place(hireId, projectId, BoardCardKind.SUGGESTED_TASKS),
        )
    }

    @Test
    fun `a dismissed current-task card comes back when the hire grabs a task`() {
        val board = existingBoard()
        val dismissed = card(board, BoardCardKind.CURRENT_TASK, state = BoardCardState.DISMISSED)
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(dismissed)

        val outcome = service.placeOrRevive(hireId, projectId, BoardCardKind.CURRENT_TASK)

        // Grabbing is the hire saying "this is what I'm working on", which is what the card says.
        assertEquals(BoardService.PlacementOutcome.PLACED, outcome)
        assertEquals(BoardCardState.ACTIVE, dismissed.state)
        verify { boardCardRepository.save(dismissed) }
    }

    @Test
    fun `the mentor's place still leaves a dismissed card alone`() {
        val board = existingBoard()
        val dismissed = card(board, BoardCardKind.CURRENT_TASK, state = BoardCardState.DISMISSED)
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(dismissed)

        assertEquals(
            BoardService.PlacementOutcome.DISMISSED_BY_HIRE,
            service.place(hireId, projectId, BoardCardKind.CURRENT_TASK),
        )
        assertEquals(BoardCardState.DISMISSED, dismissed.state)
    }

    // ---- the hire removes (slice 1) ----

    @Test
    fun `dismissing marks the card rather than deleting it`() {
        val board = existingBoard()
        val card = card(board, BoardCardKind.SUGGESTED_TASKS)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(board.id) } returns Optional.of(board)

        assertTrue(service.dismiss(hireId, card.id))

        // The surviving row is the whole mechanism: it is what later placements consult.
        assertEquals(BoardCardState.DISMISSED, card.state)
        verify { boardCardRepository.save(card) }
        verify(exactly = 0) { boardCardRepository.delete(any()) }
    }

    @Test
    fun `a card on somebody else's board answers the same as one that does not exist`() {
        val otherBoard = Board(userId = UUID.randomUUID(), projectId = projectId)
        val card = card(otherBoard, BoardCardKind.SUGGESTED_TASKS)
        val strangerId = UUID.randomUUID()
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardCardRepository.findById(strangerId) } returns Optional.empty()
        every { boardRepository.findById(otherBoard.id) } returns Optional.of(otherBoard)

        // A 403 here would confirm that a given id is a real card of somebody's.
        assertFalse(service.dismiss(hireId, card.id))
        assertFalse(service.dismiss(hireId, strangerId))
    }

    @Test
    fun `dismissing twice is not an error`() {
        val board = existingBoard()
        val card = card(board, BoardCardKind.SUGGESTED_TASKS, state = BoardCardState.DISMISSED)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(board.id) } returns Optional.of(board)

        assertTrue(service.dismiss(hireId, card.id))
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    // ---- what the placeable cards say ----

    @Test
    fun `the current-task card reads the task, and reading never assigns one`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.CURRENT_TASK),
        )
        every { currentTaskReader.currentTaskFor(hireId, projectId) } returns StarterWorkTaskProposal(
            sourceId = "github:org/repo:ISSUE:7",
            title = "Fix the flaky login test",
            summary = "It fails about one run in five.",
            sourceUrl = "https://example.test/issues/7",
        )
        every { currentTaskReader.isClaimedGoal(hireId, projectId) } returns true

        val content = service.currentTaskCard()

        assertEquals("Fix the flaky login test", content.title)
        // Chosen, not handed: only one of those is theirs to change their mind about.
        assertTrue(content.chosen)
    }

    @Test
    fun `a hire with no task still gets the card, saying so`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.CURRENT_TASK),
        )

        val content = service.currentTaskCard()

        // A card that vanishes when the goal is cleared reads as the board losing things.
        assertNull(content.taskId)
        assertFalse(content.chosen)
    }

    @Test
    fun `the suggestions card carries the reasons and no score`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.SUGGESTED_TASKS),
        )
        every { starterWorkTaskProposalService.matchForUserId(hireId, projectId) } returns listOf(
            ranked("Fix a typo", listOf("You have worked in this repository before")),
        )

        val tasks = service.suggestionsCard().tasks

        assertEquals(listOf("You have worked in this repository before"), tasks.first().reasons)
    }

    @Test
    fun `the suggestions and the pool share one ranking per board read`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.SUGGESTED_TASKS, position = 0),
            card(board, BoardCardKind.TASK_POOL, position = 1),
        )
        every { starterWorkTaskProposalService.matchForUserId(hireId, projectId) } returns listOf(
            ranked("Fix a typo", listOf("You have worked in this repository before")),
        )

        service.getBoard(hireId, projectId)

        // A pass over the whole live pool plus a responsiveness read — once, not once per card.
        verify(exactly = 1) { starterWorkTaskProposalService.matchForUserId(hireId, projectId) }
    }

    @Test
    fun `the task pool card lists the whole pool in rank order and marks the current task`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.TASK_POOL),
        )
        val pool = (1..5).map { ranked("Task $it", listOf("reason $it")) }
        every { starterWorkTaskProposalService.matchForUserId(hireId, projectId) } returns pool
        every { currentTaskReader.currentTaskFor(hireId, projectId) } returns StarterWorkTaskProposal(
            id = pool[3].task.id,
            sourceId = "src-Task 4",
            title = "Task 4",
        )

        val content = service.taskPoolCard()

        // Uncapped, unlike the suggestions card: nothing is hidden behind "ask your buddy".
        assertEquals(pool.map { it.task.title }, content.tasks.map { it.title })
        assertEquals(listOf(true, true, true, false, false), content.tasks.map { it.bestFit })
        assertEquals(pool[3].task.id, content.currentTaskId)
    }

    // ---- what the mentor's other cards say (slice 3) ----

    @Test
    fun `the competency card splits at the bar rather than summing to a percentage`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.COMPETENCY_PROGRESS),
        )
        every { myCompetencyService.getCompetenciesForUser(hireId) } returns listOf(
            competency("Kotlin", level = 3, targetLevel = 2),
            competency("Testing", level = 1, targetLevel = 2),
        )

        val content = service.competencyCard()

        assertEquals(listOf("Kotlin"), content.held.map { it.label })
        assertEquals(listOf("Testing"), content.inProgress.map { it.label })
    }

    @Test
    fun `a level-0 placement is not a competency and is left out`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.COMPETENCY_PROGRESS),
        )
        every { myCompetencyService.getCompetenciesForUser(hireId) } returns listOf(
            competency("Kubernetes", level = 0, targetLevel = 2),
        )

        val content = service.competencyCard()

        // Level 0 means "asked, saw no evidence". Reporting it would claim a skill nobody showed.
        assertTrue(content.held.isEmpty())
        assertTrue(content.inProgress.isEmpty())
    }

    @Test
    fun `the memory card shows what the mentor remembers, and how much it covers`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.MEMORY_RECAP),
        )
        every { buddySessionRepository.findByUserId(hireId) } returns BuddySession(
            userId = hireId,
            summary = "Ada is working through the login refactor and asked about our test setup.",
            summarizedCount = 12,
        )

        val content = service.memoryCard()

        assertEquals(12, content.messagesRemembered)
        assertTrue(content.memory!!.contains("login refactor"))
    }

    @Test
    fun `a hire who has never opened the buddy has no memory, and reading the card starts none`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.MEMORY_RECAP),
        )

        val content = service.memoryCard()

        // Hydrating a card must not be what starts somebody's buddy session.
        assertNull(content.memory)
        assertEquals(0, content.messagesRemembered)
    }

    private fun competency(label: String, level: Int, targetLevel: Int) = MyCompetencyResponse(
        competencyKey = label.lowercase(),
        label = label,
        kind = CompetencyKind.SKILL,
        level = level,
        targetLevel = targetLevel,
        source = CompetencySource.VERIFIED,
        updatedAt = Instant.EPOCH,
    )

    private fun BoardService.competencyCard(): CompetencyProgressContent =
        getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.COMPETENCY_PROGRESS }
            .content as CompetencyProgressContent

    private fun BoardService.memoryCard(): MemoryRecapContent =
        getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.MEMORY_RECAP }
            .content as MemoryRecapContent

    private fun card(
        board: Board,
        kind: BoardCardKind,
        owner: BoardCardOwner = BoardCardOwner.AI,
        state: BoardCardState = BoardCardState.ACTIVE,
        position: Int = 0,
    ) = BoardCard(
        boardId = board.id,
        kind = kind,
        owner = owner,
        state = state,
        position = position,
    )

    private fun ranked(title: String, reasons: List<String>) = RankedStarterWorkTaskResponse(
        task = StarterWorkTaskProposalResponse(
            id = UUID.randomUUID(),
            sourceId = "src-$title",
            title = title,
            summary = null,
            rationale = null,
            sourceUrl = null,
            competencyKeys = emptyList(),
            status = ProposalStatus.LIVE,
            taskZeroEligible = false,
            reviewed = true,
            sourceHasAssignee = null,
            sourceCheckedAt = null,
        ),
        score = 1.0,
        matchedCompetencyKeys = emptyList(),
        taskType = TaskType.BUG,
        reasons = reasons,
    )

    private fun BoardService.currentTaskCard(): CurrentTaskContent =
        getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.CURRENT_TASK }
            .content as CurrentTaskContent

    private fun BoardService.suggestionsCard(): SuggestedTasksContent =
        getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.SUGGESTED_TASKS }
            .content as SuggestedTasksContent

    private fun BoardService.taskPoolCard(): TaskPoolContent =
        getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.TASK_POOL }
            .content as TaskPoolContent

    private fun BoardService.pathCard(): PathToFirstContributionContent =
        getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.PATH_TO_FIRST_CONTRIBUTION }
            .content as PathToFirstContributionContent

    private fun BoardService.pullRequestCard(): OpenPullRequestsContent =
        getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.OPEN_PULL_REQUESTS }
            .content as OpenPullRequestsContent

    private fun List<BoardMomentResponse>.momentAt(key: BoardMomentKey): Instant? =
        first { it.key == key }.reachedAt

    // ---- PATH_STEP ----

    @Suppress("LongParameterList")
    private fun onboardingStep(
        title: String,
        id: UUID = UUID.randomUUID(),
        status: StepStatus = StepStatus.IN_PROGRESS,
        expectedOutcome: String = "Outcome",
    ): OnboardingStep {
        val path = OnboardingPath(userId = hireId)
        val phase = OnboardingPhase(path = path, position = 0, title = "Phase 1", description = "Desc")
        path.phases.add(phase)
        val step = OnboardingStep(
            id = id,
            phase = phase,
            position = 0,
            title = title,
            description = "Step description",
            type = StepType.DOCUMENT,
            estimatedMinutes = 30,
            expectedOutcome = expectedOutcome,
            status = status,
        )
        phase.steps.add(step)
        return step
    }

    private fun OnboardingStep.withTask(finished: Boolean = false, id: UUID = UUID.randomUUID()): OnboardingTask {
        val task = OnboardingTask(id = id, step = this, position = tasks.size, title = "Task", description = "Desc")
        task.finished = finished
        tasks.add(task)
        return task
    }

    private fun OnboardingStep.withResource(): OnboardingResource {
        val resource = OnboardingResource(step = this, title = "Docs", description = "Desc", url = "https://x")
        resources.add(resource)
        return resource
    }

    private fun stubPath(step: OnboardingStep) {
        every { onboardingPathRepository.findOnboardingPathByUserId(hireId) } returns Optional.of(step.phase.path)
    }

    private fun pathStepCard(
        board: Board,
        step: OnboardingStep,
        state: BoardCardState = BoardCardState.ACTIVE,
    ) = BoardCard(
        boardId = board.id,
        kind = BoardCardKind.PATH_STEP,
        owner = BoardCardOwner.AI,
        state = state,
        position = 0,
        subject = step.id.toString(),
    )

    @Test
    fun `placing a path step with no subject asks for one`() {
        existingBoard()

        assertEquals(
            BoardService.PlacementOutcome.NEEDS_A_SUBJECT,
            service.place(hireId, projectId, BoardCardKind.PATH_STEP),
        )
    }

    @Test
    fun `placing a path step by an unknown title refuses`() {
        existingBoard()
        stubPath(onboardingStep("Set up your laptop"))

        assertEquals(
            BoardService.PlacementOutcome.NO_SUCH_STEP,
            service.place(hireId, projectId, BoardCardKind.PATH_STEP, "Learn the deploy pipeline"),
        )
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    @Test
    fun `placing a path step by its title stores the resolved step id, not the title`() {
        existingBoard()
        val step = onboardingStep("Set up your laptop")
        stubPath(step)
        val saved = slot<BoardCard>()
        every { boardCardRepository.save(capture(saved)) } answers { firstArg() }

        val outcome = service.place(hireId, projectId, BoardCardKind.PATH_STEP, "  set up   YOUR laptop ")

        assertEquals(BoardService.PlacementOutcome.PLACED, outcome)
        assertEquals(step.id.toString(), saved.captured.subject)
    }

    @Test
    fun `the same step under a different phrasing is already there`() {
        val board = existingBoard()
        val step = onboardingStep("Set up your laptop")
        stubPath(step)
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(pathStepCard(board, step))

        assertEquals(
            BoardService.PlacementOutcome.ALREADY_THERE,
            service.place(hireId, projectId, BoardCardKind.PATH_STEP, "SET UP YOUR LAPTOP"),
        )
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    @Test
    fun `a dismissed path step card is not put back`() {
        val board = existingBoard()
        val step = onboardingStep("Set up your laptop")
        stubPath(step)
        every { boardCardRepository.findAllByBoardId(board.id) } returns
            listOf(pathStepCard(board, step, state = BoardCardState.DISMISSED))

        assertEquals(
            BoardService.PlacementOutcome.DISMISSED_BY_HIRE,
            service.place(hireId, projectId, BoardCardKind.PATH_STEP, "Set up your laptop"),
        )
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    @Test
    fun `a path step card reads its tasks, outcome and resources live`() {
        val board = existingBoard()
        val step = onboardingStep("Set up your laptop", expectedOutcome = "A machine that builds")
        val task = step.withTask()
        step.withResource()
        stubPath(step)
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(pathStepCard(board, step))

        val content = service
            .getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.PATH_STEP }
            .content as PathStepContent

        assertEquals(step.id, content.stepId)
        assertEquals(listOf("A machine that builds"), content.expectedOutcomes)
        assertEquals(listOf(task.id), content.tasks.map { it.id })
        assertEquals(1, content.resources.size)
        assertNull(content.reason)
    }

    @Test
    fun `a path step card reflects a task flipped underneath it, on the next read`() {
        val board = existingBoard()
        val step = onboardingStep("Set up your laptop")
        val task = step.withTask()
        stubPath(step)
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(pathStepCard(board, step))

        fun taskFinished() = service
            .getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.PATH_STEP }
            .content
            .let { (it as PathStepContent).tasks.first().finished }

        assertFalse(taskFinished())
        task.finished = true
        assertTrue(taskFinished())
    }

    @Test
    fun `a path step card whose step is gone hydrates with a reason and no step id`() {
        val board = existingBoard()
        // The path no longer holds the step the card's subject names.
        stubPath(onboardingStep("A different step"))
        every { boardCardRepository.findAllByBoardId(board.id) } returns
            listOf(
                BoardCard(
                    boardId = board.id,
                    kind = BoardCardKind.PATH_STEP,
                    owner = BoardCardOwner.AI,
                    position = 0,
                    subject = UUID.randomUUID().toString(),
                ),
            )

        val content = service
            .getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.PATH_STEP }
            .content as PathStepContent

        assertNull(content.stepId)
        assertNotNull(content.reason)
        assertTrue(content.tasks.isEmpty())
    }

    @Test
    fun `ticking a task on a path step card writes back to the path and leaves its status alone`() {
        val board = existingBoard()
        val step = onboardingStep("Set up your laptop", status = StepStatus.IN_PROGRESS)
        val task = step.withTask()
        stubPath(step)
        val card = pathStepCard(board, step)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(board.id) } returns Optional.of(board)
        every { onboardingTaskService.setFinishedForUser(hireId, task.id, true) } answers { task.finished = true }

        val content = service.tickPathStepTask(hireId, card.id, task.id, true)

        assertTrue(content.tasks.first { it.id == task.id }.finished)
        assertEquals(StepStatus.IN_PROGRESS, step.status)
        verify(exactly = 1) { onboardingTaskService.setFinishedForUser(hireId, task.id, true) }
    }

    @Test
    fun `ticking a task is idempotent`() {
        val board = existingBoard()
        val step = onboardingStep("Set up your laptop")
        val task = step.withTask(finished = true)
        stubPath(step)
        val card = pathStepCard(board, step)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(board.id) } returns Optional.of(board)
        every { onboardingTaskService.setFinishedForUser(hireId, task.id, true) } just runs

        val content = service.tickPathStepTask(hireId, card.id, task.id, true)

        assertTrue(content.tasks.first { it.id == task.id }.finished)
    }

    @Test
    fun `ticking a task on somebody else's card is a 404`() {
        val otherBoard = Board(userId = UUID.randomUUID(), projectId = projectId)
        val step = onboardingStep("Set up your laptop")
        val task = step.withTask()
        val card = pathStepCard(otherBoard, step)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(otherBoard.id) } returns Optional.of(otherBoard)

        assertThrows<ResponseStatusException> {
            service.tickPathStepTask(hireId, card.id, task.id, true)
        }.also { assertEquals(404, it.statusCode.value()) }
    }

    @Test
    fun `ticking a task on a card that is not a path step is a 404`() {
        val board = existingBoard()
        val card = card(board, BoardCardKind.CURRENT_TASK)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(board.id) } returns Optional.of(board)

        assertThrows<ResponseStatusException> {
            service.tickPathStepTask(hireId, card.id, UUID.randomUUID(), true)
        }.also { assertEquals(404, it.statusCode.value()) }
    }

    @Test
    fun `ticking a task that belongs to another step is a 404`() {
        val board = existingBoard()
        val step = onboardingStep("Set up your laptop")
        step.withTask()
        stubPath(step)
        val card = pathStepCard(board, step)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(board.id) } returns Optional.of(board)

        assertThrows<ResponseStatusException> {
            service.tickPathStepTask(hireId, card.id, UUID.randomUUID(), true)
        }.also { assertEquals(404, it.statusCode.value()) }
    }

    // -- Buddy edits to a checklist ---------------------------------------------------------------

    // Real UUIDs, not readable stand-ins: every response parses item ids with UUID.fromString, so a
    // fixture id like "i1" fails in the mapping before the test gets to assert anything.
    private val firstLineId = UUID.randomUUID().toString()
    private val secondLineId = UUID.randomUUID().toString()

    private fun checklistCard(
        board: Board,
        state: BoardCardState = BoardCardState.ACTIVE,
        items: List<ChecklistItemPayload> = listOf(
            ChecklistItemPayload(id = firstLineId, text = "Run it locally", done = true),
            ChecklistItemPayload(id = secondLineId, text = "Fix it"),
        ),
    ) = BoardCard(
        boardId = board.id,
        kind = BoardCardKind.CHECKLIST,
        owner = BoardCardOwner.HIRE,
        state = state,
        position = 0,
        payload = json.encodeToString<BoardCardPayload>(
            ChecklistPayload(title = "Getting started", items = items),
        ),
    )

    private fun savedChecklist(card: BoardCard): ChecklistPayload =
        assertNotNull(json.decodeFromString<BoardCardPayload>(assertNotNull(card.payload)) as? ChecklistPayload)

    private fun onBoard(card: BoardCard, board: Board) {
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns board
        every { boardCardRepository.findLockedById(card.id) } returns card
    }

    /**
     * A confirm can arrive long after its proposal, and the hire may have left the project since.
     * This used to write first and check membership after, so the refusal came back over a write
     * that had already been saved.
     */
    @Test
    fun `a buddy edit from a hire who is no longer a member reads and writes nothing`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        assertFailsWith<ResponseStatusException> {
            service.appendChecklistItems(hireId, projectId, UUID.randomUUID(), listOf("Open a PR"))
        }

        verify(exactly = 0) { boardCardRepository.findLockedById(any()) }
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    /** A proposal made on one project, confirmed after the hire moved to another. */
    @Test
    fun `a buddy edit refuses a checklist on another project's board`() {
        val thisBoard = Board(userId = hireId, projectId = projectId)
        val card = checklistCard(Board(userId = hireId, projectId = UUID.randomUUID()))
        onBoard(card, thisBoard)

        assertFailsWith<ResponseStatusException> {
            service.tickChecklistItems(hireId, projectId, card.id, listOf("Fix it"))
        }

        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    /** Taking a card off the board is the hire's gesture, and a stale proposal does not undo it. */
    @Test
    fun `a buddy edit refuses a checklist the hire has dismissed`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = checklistCard(board, state = BoardCardState.DISMISSED)
        onBoard(card, board)

        assertFailsWith<ResponseStatusException> {
            service.rewordChecklistItem(hireId, projectId, card.id, "Fix it", "Fix the redirect")
        }

        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    /**
     * The guarantee the amendment rests on, pinned where it is enforced: the hire's lines come
     * back with the same ids, words and ticks, and the new ones land after them. And the card is
     * read through the lock, so two edits at once cannot each start from the same payload.
     */
    @Test
    fun `appending keeps every existing line as it was and reads the card under a lock`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = checklistCard(board)
        onBoard(card, board)

        service.appendChecklistItems(hireId, projectId, card.id, listOf("Open a PR"))

        val saved = assertNotNull(card.payload)
        val checklist = assertNotNull(json.decodeFromString<BoardCardPayload>(saved) as? ChecklistPayload)
        assertEquals(listOf(firstLineId, secondLineId), checklist.items.take(2).map { it.id })
        assertEquals(listOf("Run it locally", "Fix it", "Open a PR"), checklist.items.map { it.text })
        assertEquals(listOf(true, false, false), checklist.items.map { it.done })
        verify(exactly = 1) { boardCardRepository.findLockedById(card.id) }
    }

    /**
     * Set only, and matched the way a hire would say it: trimmed and case-insensitive. A line that
     * is already done stays done and is not counted, so the count is what actually changed.
     */
    @Test
    fun `ticking sets only the named lines and counts only the new ticks`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = checklistCard(board)
        onBoard(card, board)

        val ticked = service.tickChecklistItems(hireId, projectId, card.id, listOf("  fix IT ", "Run it locally"))

        assertEquals(1, ticked)
        val checklist = savedChecklist(card)
        assertEquals(listOf(firstLineId, secondLineId), checklist.items.map { it.id })
        assertEquals(listOf("Run it locally", "Fix it"), checklist.items.map { it.text })
        assertEquals(listOf(true, true), checklist.items.map { it.done })
        verify(exactly = 1) { boardCardRepository.findLockedById(card.id) }
    }

    /** Nothing matched is nothing changed, and nothing is written. */
    @Test
    fun `ticking lines that are not on the card writes nothing`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = checklistCard(board)
        onBoard(card, board)

        val ticked = service.tickChecklistItems(hireId, projectId, card.id, listOf("Deploy to production"))

        assertEquals(0, ticked)
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    /** Rewording a step is not undoing it: the line keeps its id and its tick, and nothing else moves. */
    @Test
    fun `rewording keeps the line's id and tick and leaves the rest alone`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = checklistCard(board)
        onBoard(card, board)

        val reworded = service.rewordChecklistItem(
            hireId,
            projectId,
            card.id,
            before = " run IT locally",
            after = "Run it locally with the seed data",
        )

        assertTrue(reworded)
        val checklist = savedChecklist(card)
        assertEquals(listOf(firstLineId, secondLineId), checklist.items.map { it.id })
        assertEquals(listOf("Run it locally with the seed data", "Fix it"), checklist.items.map { it.text })
        assertEquals(listOf(true, false), checklist.items.map { it.done })
    }

    /** Two lines that read the same: rewording either one silently would be the wrong edit half the time. */
    @Test
    fun `rewording refuses a line that matches more than one and writes nothing`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = checklistCard(
            board,
            items = listOf(
                ChecklistItemPayload(id = firstLineId, text = "Fix it"),
                ChecklistItemPayload(id = secondLineId, text = "fix it"),
            ),
        )
        onBoard(card, board)

        val reworded = service.rewordChecklistItem(hireId, projectId, card.id, "Fix it", "Fix the redirect")

        assertFalse(reworded)
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    // -- Attribution and the buddy's reach into the hire's own cards ------------------------------

    private fun noteCard(board: Board, text: String = "Deploys run on Thursdays") = BoardCard(
        boardId = board.id,
        kind = BoardCardKind.NOTE,
        owner = BoardCardOwner.HIRE,
        position = 0,
        payload = json.encodeToString<BoardCardPayload>(NotePayload(text = text)),
    )

    private fun liveCard(
        board: Board,
        kind: BoardCardKind,
        position: Int,
        state: BoardCardState = BoardCardState.ACTIVE,
    ) = BoardCard(boardId = board.id, kind = kind, owner = BoardCardOwner.AI, state = state, position = position)

    /** The precedent `placedAt` set, extended: a placement also says whose change it was. */
    @Test
    fun `placing a card records it as the buddy's creation, dated with the placement`() {
        val board = Board(userId = hireId, projectId = projectId)
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns board
        val saved = slot<BoardCard>()
        every { boardCardRepository.save(capture(saved)) } answers { firstArg() }

        service.place(hireId, projectId, BoardCardKind.CURRENT_TASK)

        assertEquals(BoardCardChange.CREATED, saved.captured.lastChange)
        assertEquals(BoardActor.BUDDY, saved.captured.lastChangedBy)
        assertEquals(saved.captured.placedAt, saved.captured.lastChangedAt)
    }

    /**
     * A note the buddy wrote is still the hire's to edit, and the board can say who put it there —
     * both on the row and in what the client is sent.
     */
    @Test
    fun `a card the buddy writes stays the hire's and says the buddy put it there`() {
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns
            Board(userId = hireId, projectId = projectId)
        val saved = slot<BoardCard>()
        every { boardCardRepository.save(capture(saved)) } answers { firstArg() }

        val response = service.addAuthoredCard(
            hireId,
            projectId,
            NoteCardRequest(text = "Ask Sam about CI"),
            BoardActor.BUDDY,
        )

        assertEquals(BoardCardOwner.HIRE, saved.captured.owner)
        assertNotNull(saved.captured.placedAt)
        assertEquals(BoardActor.BUDDY, response.lastChange?.by)
        assertEquals(BoardCardChange.CREATED, response.lastChange?.change)
    }

    /** Hire-made changes are never labelled as the buddy's. */
    @Test
    fun `a card the hire writes is attributed to the hire and not dated as a placement`() {
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns
            Board(userId = hireId, projectId = projectId)
        val saved = slot<BoardCard>()
        every { boardCardRepository.save(capture(saved)) } answers { firstArg() }

        service.addAuthoredCard(hireId, projectId, NoteCardRequest(text = "My own note"))

        assertNull(saved.captured.placedAt)
        assertEquals(BoardActor.HIRE, saved.captured.lastChangedBy)
    }

    /** "Ticked two off" and "rewrote your list" are different things to be told about a card. */
    @Test
    fun `the hire ticking their own checklist is recorded as a tick by the hire`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = checklistCard(board)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(board.id) } returns Optional.of(board)

        service.editAuthoredCard(
            hireId,
            card.id,
            ChecklistCardRequest(
                title = "Getting started",
                items = listOf(
                    ChecklistItemRequest(id = UUID.fromString(firstLineId), text = "Run it locally", done = true),
                    ChecklistItemRequest(id = UUID.fromString(secondLineId), text = "Fix it", done = true),
                ),
            ),
        )

        assertEquals(BoardCardChange.TICKED, card.lastChange)
        assertEquals(BoardActor.HIRE, card.lastChangedBy)
    }

    @Test
    fun `a buddy edit rewrites the hire's note and says the buddy changed it`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = noteCard(board)
        onBoard(card, board)

        service.editAuthoredCardForBuddy(hireId, projectId, card.id, NoteCardRequest(text = "Deploys run on Tuesdays"))

        val saved = json.decodeFromString<BoardCardPayload>(assertNotNull(card.payload))
        assertEquals(NotePayload(text = "Deploys run on Tuesdays"), saved)
        assertEquals(BoardCardChange.EDITED, card.lastChange)
        assertEquals(BoardActor.BUDDY, card.lastChangedBy)
        assertNotNull(card.lastChangedAt)
    }

    /**
     * Tidying a list must not undo the work already ticked off it: a line whose words survive keeps
     * its id and its tick, wherever it moves to. A dropped line is gone; a new one starts unticked.
     */
    @Test
    fun `a buddy checklist edit keeps the ids and ticks of the lines that survive it`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = checklistCard(board)
        onBoard(card, board)

        service.editAuthoredCardForBuddy(
            hireId,
            projectId,
            card.id,
            ChecklistCardRequest(
                title = "First week",
                items = listOf(
                    ChecklistItemRequest(text = "Fix it"),
                    ChecklistItemRequest(text = " run IT locally "),
                    // A client-supplied tick on a new line is not trusted: new lines start open.
                    ChecklistItemRequest(text = "Open a PR", done = true),
                ),
            ),
        )

        val checklist = savedChecklist(card)
        assertEquals("First week", checklist.title)
        assertEquals(listOf(secondLineId, firstLineId), checklist.items.take(2).map { it.id })
        assertEquals(listOf(false, true, false), checklist.items.map { it.done })
        assertEquals(BoardActor.BUDDY, card.lastChangedBy)
    }

    /** The widening is to the hire's own cards of the kind proposed — nothing else. */
    @Test
    fun `a buddy edit refuses a card of another kind than the proposal named`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = noteCard(board)
        onBoard(card, board)

        assertFailsWith<ResponseStatusException> {
            service.editAuthoredCardForBuddy(hireId, projectId, card.id, LinkCardRequest(url = "https://example.com"))
        }

        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    /**
     * Only active cards on this project's board leave, and a card the hire had already taken off
     * keeps the hire's attribution rather than being re-dismissed as the buddy's.
     */
    @Test
    fun `a buddy dismissal takes only active cards off and attributes each to the buddy`() {
        val board = Board(userId = hireId, projectId = projectId)
        val stale = noteCard(board)
        val alreadyGone = liveCard(board, BoardCardKind.SUGGESTED_TASKS, 1, BoardCardState.DISMISSED)
            .apply { recordChange(BoardCardChange.DISMISSED, BoardActor.HIRE) }
        val untouched = liveCard(board, BoardCardKind.CURRENT_TASK, 2)
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns board
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(stale, alreadyGone, untouched)

        val gone = service.dismissForBuddy(hireId, projectId, listOf(stale.id, alreadyGone.id, UUID.randomUUID()))

        assertEquals(1, gone)
        assertEquals(BoardCardState.DISMISSED, stale.state)
        assertEquals(BoardActor.BUDDY, stale.lastChangedBy)
        assertEquals(BoardActor.HIRE, alreadyGone.lastChangedBy)
        assertEquals(BoardCardState.ACTIVE, untouched.state)
        assertNull(untouched.lastChange)
    }

    /** A stale confirm from somebody who has left the project reads nothing and writes nothing. */
    @Test
    fun `a buddy dismissal from a hire who is no longer a member reads nothing`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        assertFailsWith<ResponseStatusException> {
            service.dismissForBuddy(hireId, projectId, listOf(UUID.randomUUID()))
        }

        verify(exactly = 0) { boardCardRepository.findAllByBoardId(any()) }
        verify(exactly = 0) { boardCardRepository.saveAll(any<List<BoardCard>>()) }
    }

    /**
     * Only cards that actually moved are marked, and a dismissed card is renumbered without being
     * marked — overwriting "dismissed" with "moved" would lose the one change worth seeing on it.
     */
    @Test
    fun `a buddy reorder marks only the cards that moved and never a dismissed one`() {
        val board = Board(userId = hireId, projectId = projectId)
        val first = liveCard(board, BoardCardKind.CURRENT_TASK, 0)
        val dismissed = liveCard(board, BoardCardKind.SUGGESTED_TASKS, 1, BoardCardState.DISMISSED)
            .apply { recordChange(BoardCardChange.DISMISSED, BoardActor.HIRE) }
        val last = liveCard(board, BoardCardKind.MEMORY_RECAP, 2)
        val stays = liveCard(board, BoardCardKind.COMPETENCY_PROGRESS, 3)
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns board
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(first, dismissed, last, stays)

        val moved = service.reorderForBuddy(hireId, projectId, listOf(last.id))

        assertEquals(listOf(0, 1, 2, 3), listOf(last, first, dismissed, stays).map { it.position })
        assertEquals(1, moved)
        assertEquals(BoardCardChange.MOVED, last.lastChange)
        assertEquals(BoardActor.BUDDY, last.lastChangedBy)
        // Renumbered, but still in the same place relative to everything else: not moved.
        assertNull(first.lastChange)
        assertEquals(BoardCardChange.DISMISSED, dismissed.lastChange)
        assertEquals(BoardActor.HIRE, dismissed.lastChangedBy)
        assertNull(stays.lastChange)
    }

    @Test
    fun `the hire's own reorder is attributed to the hire`() {
        val board = Board(userId = hireId, projectId = projectId)
        val first = liveCard(board, BoardCardKind.CURRENT_TASK, 0)
        val second = liveCard(board, BoardCardKind.MEMORY_RECAP, 1)
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns board
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(first, second)

        service.reorder(hireId, projectId, listOf(second.id))

        assertEquals(BoardCardChange.MOVED, second.lastChange)
        assertEquals(BoardActor.HIRE, second.lastChangedBy)
        assertNull(first.lastChange)
    }

    /**
     * Putting one card first renumbers every card above it. Only the one that was picked up moved;
     * the rest keep their attribution — here, the buddy's edit the hire's drag must not overwrite.
     */
    @Test
    fun `moving one card to the top marks only that card, even when the whole board is resent`() {
        val board = Board(userId = hireId, projectId = projectId)
        val cards = listOf(
            BoardCardKind.CURRENT_TASK,
            BoardCardKind.MEMORY_RECAP,
            BoardCardKind.COMPETENCY_PROGRESS,
            BoardCardKind.SUGGESTED_TASKS,
        ).mapIndexed { i, kind -> liveCard(board, kind, i) }
        cards[1].recordChange(BoardCardChange.EDITED, BoardActor.BUDDY)
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns board
        every { boardCardRepository.findAllByBoardId(board.id) } returns cards

        // The client sends the whole board in its new order, as a drag does.
        service.reorder(hireId, projectId, listOf(cards[3], cards[0], cards[1], cards[2]).map { it.id })

        assertEquals(listOf(1, 2, 3, 0), cards.map { it.position })
        assertEquals(BoardCardChange.MOVED, cards[3].lastChange)
        assertNull(cards[0].lastChange)
        assertEquals(BoardCardChange.EDITED, cards[1].lastChange)
        assertEquals(BoardActor.BUDDY, cards[1].lastChangedBy)
    }

    /** Every buddy checklist write — append, tick, reword — lands attributed, not only the new ones. */
    @Test
    fun `the buddy's existing checklist writes are attributed to the buddy too`() {
        val board = Board(userId = hireId, projectId = projectId)
        val card = checklistCard(board)
        onBoard(card, board)

        service.tickChecklistItems(hireId, projectId, card.id, listOf("Fix it"))
        assertEquals(BoardCardChange.TICKED, card.lastChange)
        assertEquals(BoardActor.BUDDY, card.lastChangedBy)

        service.appendChecklistItems(hireId, projectId, card.id, listOf("Open a PR"))
        assertEquals(BoardCardChange.EDITED, card.lastChange)
        assertEquals(BoardActor.BUDDY, card.lastChangedBy)
    }
}
