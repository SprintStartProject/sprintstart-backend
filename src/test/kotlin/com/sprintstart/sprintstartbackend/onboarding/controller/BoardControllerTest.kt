package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BoardCardOwner
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.AuthoredCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.board.NoteCardRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardCardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardMomentKey
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardMomentResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.BoardVocabularyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.NoteContent
import com.sprintstart.sprintstartbackend.onboarding.model.response.board.PathToFirstContributionContent
import com.sprintstart.sprintstartbackend.onboarding.service.BoardDiagramService
import com.sprintstart.sprintstartbackend.onboarding.service.BoardService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@WebMvcTest(BoardController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class BoardControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var boardService: BoardService

    @MockkBean
    private lateinit var boardDiagramService: BoardDiagramService

    @MockkBean
    private lateinit var userApi: UserApi

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val authId = "test-auth-id"
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    private fun jwtWithSubject(subject: String, vararg roles: String): JwtRequestPostProcessor =
        jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    private val userJwt = jwtWithSubject(authId, "USER")

    private fun board() = BoardResponse(
        boardId = UUID.randomUUID(),
        projectId = projectId,
        vocabulary = BoardVocabularyResponse(
            trackLabel = "Scrum Master",
            contributionNoun = "ceremony",
            contributionNounPlural = "ceremonies",
            contributionVerbPast = "facilitated",
        ),
        cards = listOf(
            BoardCardResponse(
                id = UUID.randomUUID(),
                kind = BoardCardKind.PATH_TO_FIRST_CONTRIBUTION,
                owner = BoardCardOwner.AI,
                position = 0,
                placedAt = null,
                content = PathToFirstContributionContent(
                    moments = listOf(BoardMomentResponse(BoardMomentKey.JOINED, null)),
                    acceptedCount = 0,
                    autonomyReachedAt = null,
                    stalledReason = null,
                ),
            ),
        ),
    )

    private fun noteCard() = BoardCardResponse(
        id = UUID.randomUUID(),
        kind = BoardCardKind.NOTE,
        owner = BoardCardOwner.HIRE,
        position = 2,
        placedAt = null,
        content = NoteContent(text = "deploys are on Thursdays"),
    )

    @Test
    fun `getMyBoard returns the caller's board`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { boardService.getBoard(userId, projectId) } returns board()

        mockMvc
            .perform(get("/api/v1/onboarding/me/board").param("projectId", projectId.toString()).with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.vocabulary.contributionNoun").value("ceremony"))
            // The card's kind must survive onto the wire as the content's discriminator, or a
            // client cannot tell which card it is rendering.
            .andExpect(jsonPath("$.cards[0].content.kind").value("PATH_TO_FIRST_CONTRIBUTION"))
            .andExpect(jsonPath("$.cards[0].content.moments[0].key").value("JOINED"))
            .andExpect(jsonPath("$.cards[0].content.moments[0].reachedAt").doesNotExist())
    }

    @Test
    fun `getMyBoard is 404 for a project the caller is not on`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { boardService.getBoard(userId, projectId) } returns null

        mockMvc
            .perform(get("/api/v1/onboarding/me/board").param("projectId", projectId.toString()).with(userJwt))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `dismissCard removes a card from the caller's own board`() {
        val cardId = UUID.randomUUID()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { boardService.dismiss(userId, cardId) } returns true

        mockMvc
            .perform(delete("/api/v1/onboarding/me/board/cards/$cardId").with(userJwt))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `dismissCard is 404 for a card that is not on a board of theirs`() {
        val cardId = UUID.randomUUID()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { boardService.dismiss(userId, cardId) } returns false

        // Same answer as a card that does not exist: a 403 would confirm the id is somebody's card.
        mockMvc
            .perform(delete("/api/v1/onboarding/me/board/cards/$cardId").with(userJwt))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `dismissCard requires authentication`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/me/board/cards/${UUID.randomUUID()}"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `addCard takes a note and answers with the card`() {
        val request = slot<AuthoredCardRequest>()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { boardService.addAuthoredCard(eq(userId), eq(projectId), capture(request)) } returns
            noteCard()

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/board/cards")
                    .param("projectId", projectId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"kind":"NOTE","text":"deploys are on Thursdays"}""")
                    .with(userJwt),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.owner").value("HIRE"))
            .andExpect(jsonPath("$.content.text").value("deploys are on Thursdays"))

        // The wire kind has to pick the right request shape, or a note could arrive as a link.
        assertTrue(request.captured is NoteCardRequest)
    }

    @Test
    fun `addCard relays a refusal to keep a card that would say nothing`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { boardService.addAuthoredCard(any(), any(), any()) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST, "A note needs some text")

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/board/cards")
                    .param("projectId", projectId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"kind":"NOTE","text":"   "}""")
                    .with(userJwt),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `editCard replaces what one of the caller's cards says`() {
        val cardId = UUID.randomUUID()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { boardService.editAuthoredCard(eq(userId), eq(cardId), any()) } returns noteCard()

        mockMvc
            .perform(
                patch("/api/v1/onboarding/me/board/cards/$cardId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"kind":"NOTE","text":"deploys are on Thursdays"}""")
                    .with(userJwt),
            ).andExpect(status().isOk)
    }

    @Test
    fun `reorder sends the whole order`() {
        val request = slot<List<UUID>>()
        val first = UUID.randomUUID()
        val second = UUID.randomUUID()
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { boardService.reorder(eq(userId), eq(projectId), capture(request)) } returns Unit

        mockMvc
            .perform(
                put("/api/v1/onboarding/me/board/order")
                    .param("projectId", projectId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"cardIds":["$first","$second"]}""")
                    .with(userJwt),
            ).andExpect(status().isNoContent)

        assertEquals(listOf(first, second), request.captured)
    }

    @Test
    fun `authoring requires authentication`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/board/cards")
                    .param("projectId", projectId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"kind":"NOTE","text":"hello"}"""),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `getMyBoard requires authentication`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/board").param("projectId", projectId.toString()))
            .andExpect(status().isUnauthorized)
    }
}
