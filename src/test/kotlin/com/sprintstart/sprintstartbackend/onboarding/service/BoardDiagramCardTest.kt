package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Board
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardDiagram
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.DiagramContent
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The mentor draws on the board: what a diagram card is identified by, and what a board read serves.
 *
 * Split from [BoardServiceTest] the way [BoardAuthoringTest] was — that one is about what the live
 * cards say, this one about the one card whose identity is a *question*. The cache behind it is
 * [BoardDiagramServiceTest]'s subject; nothing here calls the AI at all.
 */
class BoardDiagramCardTest {
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

    private val hireId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()

    private val service = BoardService(
        boardRepository,
        boardCardRepository,
        projectMembershipApi,
        trackService,
        onboardingMetricsService,
        OpenPullRequestReader(artifactIngestionApi),
        currentTaskReader,
        starterWorkTaskProposalService,
        myCompetencyService,
        buddySessionRepository,
        boardDiagramRepository,
        BoardDiagramService(
            boardRepository,
            boardCardRepository,
            boardDiagramRepository,
            onboardingAiClient,
            transactionManager,
        ),
        arrivalStepService,
    )

    @BeforeEach
    fun setUp() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(
            ProjectMember(
                userId = hireId,
                displayName = "Sam Hire",
                githubLogin = "ada",
                joinedAt = Instant.parse("2026-07-01T09:00:00Z"),
            ),
        )
        every { trackService.forMember(any()) } returns engineering
        // No timeline at all: day one is the normal state, and none of these tests are about it.
        every { onboardingMetricsService.getHireTimeline(hireId, projectId) } returns null
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "ada") } returns emptyList()
        every { boardRepository.save(any()) } answers { firstArg() }
        every { boardCardRepository.save(any()) } answers { firstArg() }
        every { boardCardRepository.saveAll(any<List<BoardCard>>()) } answers { firstArg() }
        every { boardCardRepository.findAllByBoardId(any()) } returns emptyList()
        every { boardDiagramRepository.findAllByCardIdIn(any()) } returns emptyList()
        every { currentTaskReader.currentTaskFor(hireId, projectId) } returns null
        every { currentTaskReader.isClaimedGoal(hireId, projectId) } returns false
        every { starterWorkTaskProposalService.matchForUserId(hireId, projectId) } returns emptyList()
        every { myCompetencyService.getCompetenciesForUser(hireId) } returns emptyList()
        every { buddySessionRepository.findByUserId(hireId) } returns null
    }

    @Test
    fun `a diagram is placed with the question it answers`() {
        existingBoard()
        val saved = slot<BoardCard>()
        every { boardCardRepository.save(capture(saved)) } answers { firstArg() }

        assertEquals(
            BoardService.PlacementOutcome.PLACED,
            service.place(hireId, projectId, BoardCardKind.DIAGRAM, "How auth   flows here"),
        )

        // The question is stored; the picture never is, so a diagram cannot describe code that moved.
        assertEquals("How auth flows here", saved.captured.subject)
    }

    @Test
    fun `a diagram of nothing is refused rather than placed blank`() {
        existingBoard()

        // A diagram is *of* something. Placing one without a question leaves a frame the hire can
        // neither read nor fix.
        assertEquals(
            BoardService.PlacementOutcome.NEEDS_A_SUBJECT,
            service.place(hireId, projectId, BoardCardKind.DIAGRAM, "   "),
        )
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    @Test
    fun `two subjects are two diagrams`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns
            listOf(diagramCard(board, "how auth flows here"))

        // The only non-authored kind a board may hold several of: repurposing the existing card
        // would take away a picture the hire chose to keep.
        assertEquals(
            BoardService.PlacementOutcome.PLACED,
            service.place(hireId, projectId, BoardCardKind.DIAGRAM, "what the ingestion pipeline is made of"),
        )
    }

    @Test
    fun `the same question asked again is the same card, however it is capitalised`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns
            listOf(diagramCard(board, "how auth flows here"))

        assertEquals(
            BoardService.PlacementOutcome.ALREADY_THERE,
            service.place(hireId, projectId, BoardCardKind.DIAGRAM, "How Auth Flows Here"),
        )
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    @Test
    fun `a dismissed diagram stays gone even when the question is rephrased in capitals`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            diagramCard(board, "how auth flows here", state = BoardCardState.DISMISSED),
        )

        assertEquals(
            BoardService.PlacementOutcome.DISMISSED_BY_HIRE,
            service.place(hireId, projectId, BoardCardKind.DIAGRAM, "  HOW AUTH FLOWS HERE  "),
        )
        verify(exactly = 0) { boardCardRepository.save(any()) }
    }

    @Test
    fun `a board serves the picture last drawn, dated, without redrawing it`() {
        val board = existingBoard()
        val card = diagramCard(board, "how auth flows here")
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(card)
        every { boardDiagramRepository.findAllByCardIdIn(listOf(card.id)) } returns listOf(
            BoardDiagram(cardId = card.id, corpusFingerprint = "corpus-1", payload = KEPT_PAYLOAD),
        )

        val content = diagramContent()

        // Opening a board must not wait on a model, so this is the kept picture -- and it is dated,
        // because a picture served from a cache is a claim about code as it was at a moment.
        assertEquals(listOf("AuthFilter"), content.nodes.map { it.label })
        assertNotNull(content.assembledAt)
        assertEquals("how auth flows here", content.subject)
    }

    @Test
    fun `a diagram nobody has drawn yet says so rather than showing an empty frame`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns
            listOf(diagramCard(board, "how auth flows here"))

        val content = diagramContent()

        assertTrue(content.nodes.isEmpty())
        assertNull(content.assembledAt)
        assertNotNull(content.reason)
    }

    @Test
    fun `no diagram is ever part of the baseline`() {
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns null
        val saved = slot<List<BoardCard>>()
        every { boardCardRepository.saveAll(capture(saved)) } answers { firstArg() }

        service.getBoard(hireId, projectId)

        // A diagram needs a question, and the baseline has nobody to ask one.
        assertFalse(saved.captured.any { it.kind == BoardCardKind.DIAGRAM })
    }

    private fun diagramContent(): DiagramContent =
        service
            .getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.DIAGRAM }
            .content as DiagramContent

    private fun existingBoard(): Board {
        val board = Board(userId = hireId, projectId = projectId)
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns board
        return board
    }

    private fun diagramCard(
        board: Board,
        subject: String,
        state: BoardCardState = BoardCardState.ACTIVE,
    ) = BoardCard(
        boardId = board.id,
        kind = BoardCardKind.DIAGRAM,
        owner = BoardCardOwner.AI,
        state = state,
        position = 0,
        subject = subject,
    )

    private companion object {
        val engineering = OnboardingTrack(
            key = OnboardingTrack.DEFAULT_KEY,
            label = "Engineering",
            contributionNoun = "change",
            contributionNounPlural = "changes",
            contributionVerbPast = "merged",
            evidenceKinds = mutableSetOf(ContributionEvidenceKind.PULL_REQUEST),
        )

        val KEPT_PAYLOAD =
            """
            {"nodes":[{"id":"f","label":"AuthFilter","kind":"COMPONENT",
                       "citations":[{"filename":"AuthFilter.kt"}]}],"edges":[]}
            """.trimIndent()
    }
}
