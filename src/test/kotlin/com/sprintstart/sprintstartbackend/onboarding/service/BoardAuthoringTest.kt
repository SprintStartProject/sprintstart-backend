package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Board
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCard
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardCardPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.ChecklistPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.NotePayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.OnboardingTrack
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.ChecklistItemRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.LinkCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.NoteContent
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardCardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardDiagramRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The half of the board the hire owns: the cards they write, and the order they put them in.
 *
 * Split from [BoardServiceTest] because the two describe genuinely different things — that one is
 * about what the board says, this one about what the hire may do to it.
 */
class BoardAuthoringTest {
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
    private val boardDiagramRepository: BoardDiagramRepository = mockk(relaxed = true)

    // Relaxed, and empty by default: arrival steps are incidental to these tests, and an
    // empty list means no arrival card is ensured, so every card assertion here is unaffected.
    private val arrivalStepService: ArrivalStepService = mockk(relaxed = true)
    private val onboardingAiClient: OnboardingAiClient = mockk()
    private val transactionManager: PlatformTransactionManager = mockk(relaxed = true)

    private val json = Json { ignoreUnknownKeys = true }

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
                displayName = "Ada",
                githubLogin = "ada",
                joinedAt = Instant.parse("2026-07-20T09:00:00Z"),
            ),
        )
        every { trackService.forMember(any()) } returns OnboardingTrack(
            key = OnboardingTrack.DEFAULT_KEY,
            label = "Engineering",
            contributionNoun = "change",
            contributionNounPlural = "changes",
            contributionVerbPast = "merged",
            evidenceKinds = mutableSetOf(ContributionEvidenceKind.PULL_REQUEST),
        )
        every { onboardingMetricsService.getHireTimeline(hireId, projectId) } returns null
        every { artifactIngestionApi.getAuthoredPullRequests(projectId, "ada") } returns emptyList()
        every { boardRepository.save(any()) } answers { firstArg() }
        every { boardCardRepository.save(any()) } answers { firstArg() }
        every { boardCardRepository.saveAll(any<List<BoardCard>>()) } answers { firstArg() }
        every { boardCardRepository.findAllByBoardId(any()) } returns emptyList()
        every { currentTaskReader.currentTaskFor(hireId, projectId) } returns null
        every { currentTaskReader.isClaimedGoal(hireId, projectId) } returns false
        every { starterWorkTaskProposalService.matchForUserId(hireId, projectId) } returns emptyList()
        every { myCompetencyService.getCompetenciesForUser(hireId) } returns emptyList()
        every { buddySessionRepository.findByUserId(hireId) } returns null
    }

    private fun existingBoard(): Board {
        val board = Board(userId = hireId, projectId = projectId)
        every { boardRepository.findByUserIdAndProjectId(hireId, projectId) } returns board
        return board
    }

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

    @Test
    fun `a card the hire writes is theirs, and a board can hold several`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.NOTE, position = 0),
        )
        val saved = slot<BoardCard>()
        every { boardCardRepository.save(capture(saved)) } answers { firstArg() }

        service.addAuthoredCard(hireId, projectId, NoteCardRequest(text = "  ask about the deploy  "))

        // HIRE-owned is what puts it out of the mentor's reach: a board the mentor can tidy is a
        // board the hire cannot trust to keep what they put on it.
        assertEquals(BoardCardOwner.HIRE, saved.captured.owner)
        assertEquals(1, saved.captured.position)
        assertTrue(saved.captured.payload!!.contains("ask about the deploy"))
    }

    @Test
    fun `a note with nothing in it is refused rather than kept blank`() {
        existingBoard()

        val refused = assertThrows<ResponseStatusException> {
            service.addAuthoredCard(hireId, projectId, NoteCardRequest(text = "   "))
        }

        // A blank card nobody can explain later is worse than a rejected one now.
        assertEquals(HttpStatus.BAD_REQUEST, HttpStatus.valueOf(refused.statusCode.value()))
    }

    @Test
    fun `a checklist with no items yet is allowed — that is a list about to be filled in`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns emptyList()
        val saved = slot<BoardCard>()
        every { boardCardRepository.save(capture(saved)) } answers { firstArg() }

        service.addAuthoredCard(hireId, projectId, ChecklistCardRequest(title = "Set-up"))

        assertEquals(BoardCardKind.CHECKLIST, saved.captured.kind)
    }

    @Test
    fun `each new checklist item gets its id from the server`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns emptyList()
        val saved = slot<BoardCard>()
        every { boardCardRepository.save(capture(saved)) } answers { firstArg() }

        service.addAuthoredCard(
            hireId,
            projectId,
            ChecklistCardRequest(
                items = listOf(
                    ChecklistItemRequest(text = "clone the repo"),
                    // A line the hire never filled in is not an item.
                    ChecklistItemRequest(text = "   "),
                ),
            ),
        )

        val payload = json.decodeFromString<BoardCardPayload>(saved.captured.payload!!) as ChecklistPayload
        assertEquals(1, payload.items.size)
        // Minted here rather than by the client, so two tabs adding a line cannot mint the same id.
        assertNotNull(UUID.fromString(payload.items.single().id))
    }

    @Test
    fun `ticking an item keeps its id, so a line added above cannot move the tick`() {
        val board = existingBoard()
        val itemId = UUID.randomUUID()
        val card = card(board, BoardCardKind.CHECKLIST, owner = BoardCardOwner.HIRE)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(board.id) } returns Optional.of(board)

        service.editAuthoredCard(
            hireId,
            card.id,
            ChecklistCardRequest(
                items = listOf(
                    ChecklistItemRequest(text = "run the tests"),
                    ChecklistItemRequest(id = itemId, text = "clone the repo", done = true),
                ),
            ),
        )

        val payload = json.decodeFromString<BoardCardPayload>(card.payload!!) as ChecklistPayload
        val ticked = payload.items.single { it.done }
        assertEquals(itemId.toString(), ticked.id)
        assertEquals("clone the repo", ticked.text)
    }

    @Test
    fun `the hire cannot edit a card the board or the mentor put there`() {
        val board = existingBoard()
        val card = card(board, BoardCardKind.PATH_TO_FIRST_CONTRIBUTION)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(board.id) } returns Optional.of(board)

        // A live card has nothing to edit; refusing is what stops an edit quietly attaching stored
        // content to one.
        assertThrows<ResponseStatusException> {
            service.editAuthoredCard(hireId, card.id, NoteCardRequest(text = "mine now"))
        }
    }

    @Test
    fun `editing somebody else's card answers the same as editing one that does not exist`() {
        val otherBoard = Board(userId = UUID.randomUUID(), projectId = projectId)
        val card = card(otherBoard, BoardCardKind.NOTE, owner = BoardCardOwner.HIRE)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(otherBoard.id) } returns Optional.of(otherBoard)

        val refused = assertThrows<ResponseStatusException> {
            service.editAuthoredCard(hireId, card.id, NoteCardRequest(text = "not yours"))
        }

        assertEquals(HttpStatus.NOT_FOUND, HttpStatus.valueOf(refused.statusCode.value()))
    }

    @Test
    fun `an edit of the wrong kind for that card is refused`() {
        val board = existingBoard()
        val card = card(board, BoardCardKind.NOTE, owner = BoardCardOwner.HIRE)
        every { boardCardRepository.findById(card.id) } returns Optional.of(card)
        every { boardRepository.findById(board.id) } returns Optional.of(board)

        val refused = assertThrows<ResponseStatusException> {
            service.editAuthoredCard(hireId, card.id, LinkCardRequest(url = "https://example.test"))
        }

        assertEquals(HttpStatus.BAD_REQUEST, HttpStatus.valueOf(refused.statusCode.value()))
    }

    @Test
    fun `the hire's authored card reads back as what they wrote`() {
        val board = existingBoard()
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(
            card(board, BoardCardKind.NOTE, owner = BoardCardOwner.HIRE).also {
                it.payload = json.encodeToString<BoardCardPayload>(NotePayload("deploys are on Thursdays"))
            },
        )

        val content = service
            .getBoard(hireId, projectId)!!
            .cards
            .first { it.kind == BoardCardKind.NOTE }
            .content as NoteContent

        assertEquals("deploys are on Thursdays", content.text)
    }

    // ---- the hire arranges (slice 2) ----

    @Test
    fun `reordering applies the order that was sent`() {
        val board = existingBoard()
        val first = card(board, BoardCardKind.NOTE, owner = BoardCardOwner.HIRE, position = 0)
        val second = card(board, BoardCardKind.LINK, owner = BoardCardOwner.HIRE, position = 1)
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(first, second)
        every { boardCardRepository.saveAll(any<List<BoardCard>>()) } answers { firstArg() }

        service.reorder(hireId, projectId, listOf(second.id, first.id))

        assertEquals(0, second.position)
        assertEquals(1, first.position)
    }

    @Test
    fun `cards left out of a reorder keep their order, after the ones that were listed`() {
        val board = existingBoard()
        val listed = card(board, BoardCardKind.NOTE, owner = BoardCardOwner.HIRE, position = 2)
        val omittedFirst = card(board, BoardCardKind.LINK, owner = BoardCardOwner.HIRE, position = 0)
        val omittedSecond = card(board, BoardCardKind.CHECKLIST, owner = BoardCardOwner.HIRE, position = 1)
        every { boardCardRepository.findAllByBoardId(board.id) } returns
            listOf(listed, omittedFirst, omittedSecond)
        every { boardCardRepository.saveAll(any<List<BoardCard>>()) } answers { firstArg() }

        // A client that only knows about some cards must not be able to shuffle the rest.
        service.reorder(hireId, projectId, listOf(listed.id))

        assertEquals(0, listed.position)
        assertEquals(1, omittedFirst.position)
        assertEquals(2, omittedSecond.position)
    }

    @Test
    fun `a card id that is not on this board is ignored rather than rejected`() {
        val board = existingBoard()
        val card = card(board, BoardCardKind.NOTE, owner = BoardCardOwner.HIRE, position = 0)
        every { boardCardRepository.findAllByBoardId(board.id) } returns listOf(card)
        every { boardCardRepository.saveAll(any<List<BoardCard>>()) } answers { firstArg() }

        // A stale tab arranging a card that has since been dismissed should still arrange the rest.
        service.reorder(hireId, projectId, listOf(UUID.randomUUID(), card.id))

        assertEquals(0, card.position)
    }
}
