package com.sprintstart.sprintstartbackend.chat.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.requests.CreateChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatMessagesRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatsRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.ChatMessageResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.ChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.CreateChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatMessagesResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatsResponse
import com.sprintstart.sprintstartbackend.chat.service.ChatService
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import io.mockk.every
import jakarta.validation.ConstraintViolationException
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Utility to convert exception into an http response so we can test this properly
 */
@ControllerAdvice
class ValidationExceptionHandler {
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ResponseEntity<String> {
        return ResponseEntity.badRequest().body(ex.message)
    }
}

/**
 * Spring MVC slice tests for [ChatController].
 *
 * Boots only the web layer — validates HTTP status codes, routing, request/response
 * serialization, and @Valid rejection behaviour.
 */
@WebMvcTest(ChatController::class)
@Import(
    SecurityConfig::class,
    ValidationExceptionHandler::class,
)
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChatControllerWebMvcTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var chatService: ChatService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val chatId: UUID = UUID.randomUUID()
    private val userId: UUID = UUID.randomUUID()

    private val userJwt = jwt()
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val noUserRoleJwt = jwt()
        .authorities(SimpleGrantedAuthority("ROLE_NONE"))

    private val sampleChatResponse = ChatResponse(
        id = chatId,
        title = "Sprint planning",
        userId = userId,
        createdAt = OffsetDateTime.now(),
    )

    @Nested
    inner class GetChats {
        @Test
        fun `returns 200 with valid request`() {
            val request = GetChatsRequest(limit = 5)
            every { chatService.getChats(request) } returns GetChatsResponse(chats = listOf(sampleChatResponse))

            mockMvc
                .get("/api/v1/chats?limit=5") {
                    with(userJwt)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.chats[0].id") { value(chatId.toString()) }
                    jsonPath("$.chats[0].title") { value("Sprint planning") }
                }
        }

        @Test
        fun `returns 200 with null limit (retrieve all)`() {
            val request = GetChatsRequest(limit = null)
            val chat = ChatResponse(chatId, "Sprint Planning", userId, OffsetDateTime.now())
            every { chatService.getChats(request) } returns GetChatsResponse(chats = listOf(chat))

            mockMvc
                .get("/api/v1/chats") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.chats") { chat }
                }
        }

        @Test
        fun `returns 400 when limit is less than 1`() {
            mockMvc
                .get("/api/v1/chats?limit=-5") {
                    with(userJwt)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 401 when not authenticated`() {
            mockMvc
                .get("/api/v1/chats")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `returns 403 when authenticated with wrong role`() {
            mockMvc
                .get("/api/v1/chats") {
                    with(noUserRoleJwt)
                }.andExpect {
                    status { isForbidden() }
                }
        }
    }

    @Nested
    inner class GetChatMessages {
        @Test
        fun `returns 200 with valid request`() {
            val request = GetChatMessagesRequest(limit = 20)
            every { chatService.getChat(chatId, request) } returns GetChatMessagesResponse(
                messages = listOf(ChatMessageResponse(role = ChatRole.USER, content = "Hello")),
            )

            mockMvc
                .get("/api/v1/chats/$chatId?limit=20") {
                    with(userJwt)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.messages[0].content") { value("Hello") }
                    jsonPath("$.messages[0].role") { value("USER") }
                }
        }

        @Test
        fun `returns 200 with null limit`() {
            val request = GetChatMessagesRequest(limit = null)
            every { chatService.getChat(chatId, request) } returns GetChatMessagesResponse(
                messages = listOf(ChatMessageResponse(role = ChatRole.USER, content = "Hello")),
            )

            mockMvc
                .get("/api/v1/chats/$chatId") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.messages[0].content") { value("Hello") }
                    jsonPath("$.messages[0].role") { value("USER") }
                }
        }

        @Test
        fun `returns 400 when limit is less than 1`() {
            mockMvc
                .get("/api/v1/chats/$chatId?limit=0") {
                    with(userJwt)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 400 when id path variable is not a valid UUID`() {
            mockMvc
                .get("/api/v1/chats/not-a-uuid") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(GetChatMessagesRequest(limit = null))
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 401 when not authenticated`() {
            mockMvc
                .get("/api/v1/chats/$chatId")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `returns 403 when authenticated with wrong role`() {
            mockMvc
                .get("/api/v1/chats/$chatId") {
                    with(noUserRoleJwt)
                }.andExpect {
                    status { isForbidden() }
                }
        }
    }

    @Nested
    inner class CreateChat {
        @Test
        fun `returns 201 with valid request`() {
            val request = CreateChatRequest(userId = userId)
            every { chatService.createChat(request) } returns CreateChatResponse(id = chatId)

            mockMvc
                .post("/api/v1/chats") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.id") { value(chatId.toString()) }
                }
        }

        @Test
        fun `returns 400 when userId is missing from body`() {
            mockMvc
                .post("/api/v1/chats") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = "{}"
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 400 when userId is not a valid UUID string`() {
            mockMvc
                .post("/api/v1/chats") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"userId": "not-a-uuid"}"""
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 401 when not authenticated`() {
            mockMvc
                .post("/api/v1/chats") {
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(CreateChatRequest(userId = userId))
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `returns 403 when authenticated with wrong role`() {
            mockMvc
                .post("/api/v1/chats") {
                    with(noUserRoleJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(CreateChatRequest(userId = userId))
                }.andExpect {
                    status { isForbidden() }
                }
        }
    }

    @Nested
    inner class Prompt {
        @Test
        fun `returns 200 when valid msg`() {
            val tokens = listOf(
                """{"type":"token","content":"The"}""",
                """{"type":"token","content":" goal"}""",
                """{"type":"done"}""",
            )
            every { chatService.prompt(any()) } returns flowOf(*tokens.toTypedArray())

            val mvcResult = mockMvc
                .perform(
                    post("/api/v1/chats/prompt")
                        .with(userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"chatId": "$chatId", "msg": "Test msg"}"""),
                ).andExpect(status().isOk)
                .andReturn()
                .response
                .contentAsString

            val result = mvcResult.replace("data:", "").replace("\n", "")
            assertEquals(tokens[0] + tokens[1] + tokens[2], result)
        }

        @Test
        fun `returns 400 when msg is blank`() {
            mockMvc
                .post("/api/v1/chats/prompt") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"chatId": "$chatId", "msg": ""}"""
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 400 when chatId is missing`() {
            mockMvc
                .post("/api/v1/chats/prompt") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"msg": "Hello"}"""
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 400 when chatId is not a valid UUID`() {
            mockMvc
                .post("/api/v1/chats/prompt") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"chatId": "bad-id", "msg": "Hello"}"""
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 401 when not authenticated`() {
            mockMvc
                .post("/api/v1/chats/prompt") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"chatId": "$chatId", "msg": "Hello"}"""
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `returns 403 when authenticated with wrong role`() {
            mockMvc
                .post("/api/v1/chats/prompt") {
                    with(noUserRoleJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"chatId": "$chatId", "msg": "Hello"}"""
                }.andExpect {
                    status { isForbidden() }
                }
        }
    }
}
