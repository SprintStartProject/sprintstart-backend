package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardStage
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CardDependencySource
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CardWidth
import com.sprintstart.sprintstartbackend.onboarding.external.enums.HighlightColor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Board
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardGroupPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructure
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BoardStructurePayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CardDependencyPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CardMarkPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CardOriginPayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CardSizePayload
import com.sprintstart.sprintstartbackend.onboarding.model.entity.CardStructurePayload
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BoardStructureRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class BoardStructureServiceTest {
    private val boardRepository: BoardRepository = mockk(relaxed = true)
    private val boardStructureRepository: BoardStructureRepository = mockk(relaxed = true)
    private val projectMembershipApi: ProjectMembershipApi = mockk()

    private lateinit var service: BoardStructureService

    private val userId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()
    private val board = Board(userId = userId, projectId = projectId)

    @BeforeEach
    fun setUp() {
        service = BoardStructureService(boardRepository, boardStructureRepository, projectMembershipApi)
        every { projectMembershipApi.getProjectMembers(projectId) } returns listOf(memberOf(userId))
    }

    @Test
    fun `a board nobody has arranged answers empty rather than missing`() {
        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board
        every { boardStructureRepository.findByBoardId(board.id) } returns null

        val read = service.read(userId, projectId)

        // Having arranged nothing is a normal first day, not an error a client has to special-case.
        assertEquals(BoardStructurePayload(), read?.structure)
        assertNull(read?.updatedAt)
    }

    @Test
    fun `somebody who is not on the project gets nothing to tell apart from a missing project`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        assertNull(service.read(userId, projectId))
        assertNull(service.write(userId, projectId, BoardStructurePayload()))
    }

    @Test
    fun `an arrangement survives the round trip whole`() {
        val payload = BoardStructurePayload(
            cards = mapOf(
                "c1" to CardStructurePayload(
                    stage = BoardStage.LATER,
                    dependsOn = listOf(CardDependencyPayload("c2", CardDependencySource.TEAM)),
                    markedDone = true,
                ),
            ),
            groups = listOf(BoardGroupPayload(id = "g1", name = "Paperwork", cardIds = listOf("c1"))),
            collapsedCardIds = listOf("c3"),
            pinnedCardIds = listOf("c1"),
            sizes = mapOf("c1" to CardSizePayload(CardWidth.WIDE)),
            origins = mapOf("c1" to CardOriginPayload("/knowledge-base?artifact=a1", "Deployment")),
            marks = mapOf("c1" to listOf(CardMarkPayload("on Thursdays", HighlightColor.GREEN))),
        )

        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board
        every { boardStructureRepository.findByBoardId(board.id) } returns null
        val stored = slot<BoardStructure>()
        every { boardStructureRepository.save(capture(stored)) } answers { stored.captured }

        service.write(userId, projectId, payload)

        // Read back through the same decoder the read path uses, so the test is about the stored
        // document rather than about the object that was handed in.
        every { boardStructureRepository.findByBoardId(board.id) } returns stored.captured
        assertEquals(payload, service.read(userId, projectId)?.structure)
    }

    @Test
    fun `an empty area survives being stored`() {
        // It used to be dropped when its last card left, which was right while the only way to make
        // an area was to put a card in one — and wrong once one could be made empty and named first.
        val payload = BoardStructurePayload(groups = listOf(BoardGroupPayload(id = "g1", name = "Week two")))

        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board
        every { boardStructureRepository.findByBoardId(board.id) } returns null
        val stored = slot<BoardStructure>()
        every { boardStructureRepository.save(capture(stored)) } answers { stored.captured }

        service.write(userId, projectId, payload)

        every { boardStructureRepository.findByBoardId(board.id) } returns stored.captured
        assertEquals(payload.groups, service.read(userId, projectId)?.structure?.groups)
    }

    @Test
    fun `a stored document this version cannot read answers as unarranged rather than failing`() {
        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board
        every { boardStructureRepository.findByBoardId(board.id) } returns
            BoardStructure(boardId = board.id, payload = "{not json")

        // The arrangement is the least important thing on the page; the worst it can do is take the
        // page down with it.
        assertEquals(BoardStructurePayload(), service.read(userId, projectId)?.structure)
    }

    @Test
    fun `a field this version does not know is ignored rather than refused`() {
        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board
        every { boardStructureRepository.findByBoardId(board.id) } returns
            BoardStructure(
                boardId = board.id,
                payload = """{"collapsedCardIds":["c1"],"somethingWeDropped":{"a":1}}""",
            )

        assertEquals(listOf("c1"), service.read(userId, projectId)?.structure?.collapsedCardIds)
    }

    @Test
    fun `arranging before the board has ever been read creates it`() {
        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns null
        every { boardRepository.save(any()) } returns board
        every { boardStructureRepository.findByBoardId(board.id) } returns null
        val stored = slot<BoardStructure>()
        every { boardStructureRepository.save(capture(stored)) } answers { stored.captured }

        // A client that arranges before it reads is doing nothing wrong; refusing would make the
        // order of two unrelated calls matter.
        assertTrue(service.write(userId, projectId, BoardStructurePayload()) != null)
        assertEquals(board.id, stored.captured.boardId)
    }

    private fun memberOf(id: UUID): ProjectMember = ProjectMember(
        userId = id,
        displayName = "Ada",
        githubLogin = "ada",
        joinedAt = null,
    )
}
