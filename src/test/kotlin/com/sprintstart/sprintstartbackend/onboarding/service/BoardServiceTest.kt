package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.external.enums.TaskType
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ArrivalStep
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Board
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.ArrivalStepsContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardMomentKey
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardMomentResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.CompetencyProgressContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.CurrentTaskContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.MemoryRecapContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.OpenPullRequestsContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.PathToFirstContributionContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.SuggestedTasksContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireVocabularyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.RankedStarterWorkTaskResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.starterwork.StarterWorkTaskProposalResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardDiagramRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
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
    private val trackService: TrackService = mockk()
    private val onboardingMetricsService: OnboardingMetricsService = mockk()
    private val artifactIngestionApi: ArtifactIngestionApi = mockk()
    private val currentTaskReader: CurrentTaskReader = mockk()
    private val starterWorkTaskProposalService: StarterWorkTaskProposalService = mockk()
    private val myCompetencyService: MyCompetencyService = mockk()
    private val buddySessionRepository: BuddySessionRepository = mockk()
    private val boardDiagramRepository: BoardDiagramRepository = mockk()

    // Relaxed, and empty by default: arrival steps are incidental to these tests, and an
    // empty list means no arrival card is ensured, so every card assertion here is unaffected.
    private val arrivalStepService: ArrivalStepService = mockk(relaxed = true)
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)

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
        trackService,
        onboardingMetricsService,
        OpenPullRequestReader(artifactIngestionApi, Clock.fixed(now, ZoneOffset.UTC)),
        currentTaskReader,
        starterWorkTaskProposalService,
        myCompetencyService,
        buddySessionRepository,
        boardDiagramRepository,
        boardDiagramService,
        arrivalStepService,
    )

    private val engineering = OnboardingTrack(
        key = OnboardingTrack.DEFAULT_KEY,
        label = "Engineering",
        contributionNoun = "change",
        contributionNounPlural = "changes",
        contributionVerbPast = "merged",
        evidenceKinds = mutableSetOf(ContributionEvidenceKind.PULL_REQUEST),
    )

    private val scrumMaster = OnboardingTrack(
        key = "scrum-master",
        label = "Scrum Master",
        contributionNoun = "ceremony",
        contributionNounPlural = "ceremonies",
        contributionVerbPast = "facilitated",
        evidenceKinds = mutableSetOf(ContributionEvidenceKind.ATTESTATION),
    )

    @BeforeEach
    fun setUp() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(member())
        every { trackService.forMember(any()) } returns engineering
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
        vocabulary = HireVocabularyResponse(
            trackLabel = "Engineering",
            contributionNoun = "change",
            contributionNounPlural = "changes",
            contributionVerbPast = "merged",
        ),
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
    fun `an engineering hire gets the path card and the open pull request card`() {
        noBoardYet()

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        assertEquals(
            listOf(BoardCardKind.PATH_TO_FIRST_CONTRIBUTION, BoardCardKind.OPEN_PULL_REQUESTS),
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
            listOf(BoardCardKind.PATH_TO_FIRST_CONTRIBUTION, BoardCardKind.OPEN_PULL_REQUESTS),
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

        // ⚠️ The card is still there and still honest -- "nothing claimed yet" is a real state, and
        // a card that vanished would read as the board losing things. It simply does not get the
        // best place on the board for having nothing on it.
        assertEquals(
            listOf(
                BoardCardKind.PATH_TO_FIRST_CONTRIBUTION,
                BoardCardKind.OPEN_PULL_REQUESTS,
                BoardCardKind.CURRENT_TASK,
            ),
            kinds,
        )
    }

    /**
     * ⚠️ **Arrival outranks the current task**: what has to be true before somebody can work
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

        // Only the two pinned places are asserted: the baseline cards this hire's track ensures
        // follow underneath in their own order, which is not what this test is about.
        assertEquals(
            listOf(BoardCardKind.ARRIVAL_STEPS, BoardCardKind.CURRENT_TASK),
            kinds!!.take(2),
        )
    }

    /**
     * ⚠️ **Crowding gets nothing, and this pins that.** Every auto-tidy that *removes* a card breaks
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
    fun `a track that cannot have pull requests is not given a pull request card`() {
        noBoardYet()
        every { trackService.forMember(any()) } returns scrumMaster

        val kinds = service.getBoard(hireId, projectId)?.cards?.map { it.kind }

        // Absent, not empty: a permanently empty "your open pull requests" card in front of
        // somebody who will never have one is exactly the wrong opening.
        assertEquals(listOf(BoardCardKind.PATH_TO_FIRST_CONTRIBUTION), kinds)
    }

    @Test
    fun `the path card is placed for every track, because its moments are not about git`() {
        noBoardYet()
        every { trackService.forMember(any()) } returns scrumMaster

        val board = service.getBoard(hireId, projectId)

        assertTrue(
            board?.cards.orEmpty().any { it.kind == BoardCardKind.PATH_TO_FIRST_CONTRIBUTION },
        )
    }

    @Test
    fun `the board carries the track's own words`() {
        noBoardYet()
        every { trackService.forMember(any()) } returns scrumMaster

        val vocabulary = service.getBoard(hireId, projectId)?.vocabulary

        assertEquals("ceremony", vocabulary?.contributionNoun)
        assertEquals("ceremonies", vocabulary?.contributionNounPlural)
        assertEquals("facilitated", vocabulary?.contributionVerbPast)
        assertEquals("Scrum Master", vocabulary?.trackLabel)
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
        assertEquals(listOf(BoardCardKind.PATH_TO_FIRST_CONTRIBUTION), kinds)
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
        assertEquals(BoardCardKind.OPEN_PULL_REQUESTS, cards.last().kind)
        assertEquals(8, cards.last().position)
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
    fun `the mentor cannot place a card this hire's track can never fill`() {
        existingBoard()
        every { trackService.forMember(any()) } returns scrumMaster

        // The same gate the baseline obeys. However reasonable it seemed mid-conversation, a
        // pull-request card in front of somebody whose work is never a pull request says nothing.
        assertEquals(
            BoardService.PlacementOutcome.UNSUPPORTED,
            service.place(hireId, projectId, BoardCardKind.OPEN_PULL_REQUESTS),
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
}
