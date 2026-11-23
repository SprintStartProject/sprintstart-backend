package com.sprintstart.sprintstartbackend.board.service

import com.sprintstart.sprintstartbackend.board.external.enums.BoardStage
import com.sprintstart.sprintstartbackend.board.external.enums.CardDependencySource
import com.sprintstart.sprintstartbackend.board.external.enums.CardWidth
import com.sprintstart.sprintstartbackend.board.external.enums.HighlightColor
import com.sprintstart.sprintstartbackend.board.external.model.BoardGroupPayload
import com.sprintstart.sprintstartbackend.board.external.model.BoardStructurePayload
import com.sprintstart.sprintstartbackend.board.external.model.CardDependencyPayload
import com.sprintstart.sprintstartbackend.board.external.model.CardMarkPayload
import com.sprintstart.sprintstartbackend.board.external.model.CardOriginPayload
import com.sprintstart.sprintstartbackend.board.external.model.CardSizePayload
import com.sprintstart.sprintstartbackend.board.external.model.CardStructurePayload
import com.sprintstart.sprintstartbackend.board.model.entity.Board
import com.sprintstart.sprintstartbackend.board.model.entity.BoardStructure
import com.sprintstart.sprintstartbackend.board.repository.BoardRepository
import com.sprintstart.sprintstartbackend.board.repository.BoardStructureRepository
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import com.sprintstart.sprintstartbackend.user.external.ProjectMembershipApi
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class BoardStructureServiceTest {
    private val boardRepository: BoardRepository = mockk(relaxed = true)
    private val boardStructureRepository: BoardStructureRepository = mockk(relaxed = true)
    private val projectMembershipApi: ProjectMembershipApi = mockk()

    private lateinit var service: BoardStructureService

    private val userId: UUID = UUID.randomUUID()
    private val projectId: UUID = UUID.randomUUID()
    private val project = ProjectId(projectId)
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

        val read = service.read(UserId(userId), project)

        // Having arranged nothing is a normal first day, not an error a client has to special-case.
        assertEquals(BoardStructurePayload(), read?.structure)
        assertNull(read?.updatedAt)
    }

    @Test
    fun `somebody who is not on the project gets nothing to tell apart from a missing project`() {
        every { projectMembershipApi.getProjectMembers(projectId) } returns emptyList()

        assertNull(service.read(UserId(userId), project))
        assertNull(service.write(UserId(userId), project, BoardStructurePayload()))
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
        val stored = slot<String>()

        service.write(UserId(userId), project, payload)
        verify { boardStructureRepository.upsert(any(), board.id, capture(stored), any()) }

        // Read back through the same decoder the read path uses, so the test is about the stored
        // document rather than about the object that was handed in.
        every { boardStructureRepository.findByBoardId(board.id) } returns
            BoardStructure(boardId = board.id, payload = stored.captured)
        assertEquals(payload, service.read(UserId(userId), project)?.structure)
    }

    @Test
    fun `an empty area survives being stored`() {
        // It used to be dropped when its last card left, which was right while the only way to make
        // an area was to put a card in one — and wrong once one could be made empty and named first.
        val payload = BoardStructurePayload(groups = listOf(BoardGroupPayload(id = "g1", name = "Week two")))

        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board
        val stored = slot<String>()

        service.write(UserId(userId), project, payload)
        verify { boardStructureRepository.upsert(any(), board.id, capture(stored), any()) }

        every { boardStructureRepository.findByBoardId(board.id) } returns
            BoardStructure(boardId = board.id, payload = stored.captured)
        assertEquals(payload.groups, service.read(UserId(userId), project)?.structure?.groups)
    }

    @Test
    fun `a stored document this version cannot read answers as unarranged rather than failing`() {
        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board
        every { boardStructureRepository.findByBoardId(board.id) } returns
            BoardStructure(boardId = board.id, payload = "{not json")

        // The arrangement is the least important thing on the page; the worst it can do is take the
        // page down with it.
        val read = service.read(UserId(userId), project)
        assertEquals(BoardStructurePayload(), read?.structure)
        // And it answers as an unarranged board answers, all the way down: a client told there is
        // nothing stored and handed the moment it was stored has been given two different answers.
        assertNull(read?.updatedAt)
    }

    @Test
    fun `a field this version does not know is ignored rather than refused`() {
        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board
        every { boardStructureRepository.findByBoardId(board.id) } returns
            BoardStructure(
                boardId = board.id,
                payload = """{"collapsedCardIds":["c1"],"somethingWeDropped":{"a":1}}""",
            )

        assertEquals(listOf("c1"), service.read(UserId(userId), project)?.structure?.collapsedCardIds)
    }

    @Test
    fun `arranging before the board has ever been read creates it`() {
        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns null
        every { boardRepository.save(any()) } returns board

        // A client that arranges before it reads is doing nothing wrong; refusing would make the
        // order of two unrelated calls matter.
        assertTrue(service.write(UserId(userId), project, BoardStructurePayload()) != null)
        verify { boardStructureRepository.upsert(any(), board.id, any(), any()) }
    }

    @Test
    fun `the write never looks before it stores`() {
        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board

        service.write(UserId(userId), project, BoardStructurePayload())

        // The point of the upsert: two tabs arranging the same board for the first time used to
        // both see no row and both insert, and one of them got a constraint violation for what the
        // endpoint documents as last-write-wins. A read to decide between insert and update is the
        // race, so there is no longer one.
        verify(exactly = 0) { boardStructureRepository.findByBoardId(any()) }
        verify { boardStructureRepository.upsert(any(), board.id, any(), any()) }
    }

    @Test
    fun `an arrangement larger than a board's is refused rather than stored`() {
        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board

        val huge = BoardStructurePayload(
            marks = mapOf("c1" to listOf(CardMarkPayload("x".repeat(600_000)))),
        )

        val refused =
            assertThrows<ResponseStatusException> { service.write(UserId(userId), project, huge) }

        // What it is stored as is unbounded TEXT, and what it is read into is every prompt that
        // describes this board. A client does not get to decide how large either of those is.
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, refused.statusCode)
        verify(exactly = 0) { boardStructureRepository.upsert(any(), any(), any(), any()) }
    }

    @Test
    fun `a mark may carry a colour and no words`() {
        every { boardRepository.findByUserIdAndProjectId(userId, projectId) } returns board
        val stored = slot<String>()

        // On a NOTE the marked words live in the note's own text as `==like this==`; the mark
        // itself is the colour. Requiring `text` here would have failed the whole arrangement over
        // one of them.
        val payload = BoardStructurePayload(
            marks = mapOf("c1" to listOf(CardMarkPayload(color = HighlightColor.GREEN))),
        )
        service.write(UserId(userId), project, payload)
        verify { boardStructureRepository.upsert(any(), board.id, capture(stored), any()) }

        every { boardStructureRepository.findByBoardId(board.id) } returns
            BoardStructure(boardId = board.id, payload = stored.captured)
        assertEquals(payload.marks, service.read(UserId(userId), project)?.structure?.marks)
    }

    private fun memberOf(id: UUID): ProjectMember = ProjectMember(
        userId = id,
        displayName = "Ada",
        githubLogin = "ada",
        joinedAt = null,
    )
}
