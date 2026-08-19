package com.sprintstart.sprintstartbackend.chat.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.requests.CreateChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.CreateMyChatRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatMessagesRequest
import com.sprintstart.sprintstartbackend.chat.models.requests.GetChatsRequest
import com.sprintstart.sprintstartbackend.chat.models.responses.AiStreamMessage
import com.sprintstart.sprintstartbackend.chat.models.responses.ChatMessageResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.ChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.CreateChatResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatMessagesResponse
import com.sprintstart.sprintstartbackend.chat.models.responses.GetChatsResponse
import com.sprintstart.sprintstartbackend.chat.service.ChatService
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.user.security.ProjectAuthorization
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import jakarta.validation.ConstraintViolationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
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

    /**
     * Must be mocked explicitly and by name: `POST /chats/me` guards project access through
     * `@projectAuth.canAccessProject(...)`, and the slice does not load that bean on its own.
     */
    @MockkBean(name = "projectAuth")
    private lateinit var projectAuth: ProjectAuthorization

    private val chatId: UUID = UUID.randomUUID()
    private val userId: UUID = UUID.randomUUID()
    private val authId = "auth-user"
    private val projectId: UUID = UUID.randomUUID()

    /**
     * Both project-scoped endpoints (`GET /chats/me`, `POST /chats/me`) run through the project
     * guard, so grant access to the fixture project by default. Tests covering the denial path
     * stub a different project explicitly.
     */
    @BeforeEach
    fun grantProjectAccess() {
        every { projectAuth.canAccessProject(any(), projectId) } returns true
    }

    private val userJwt = jwt()
        .jwt { it.subject(authId) }
        .authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val adminJwt = jwt()
        .jwt { it.subject(authId) }
        .authorities(SimpleGrantedAuthority("ROLE_ADMIN"))

    private val noUserRoleJwt = jwt()
        .authorities(SimpleGrantedAuthority("ROLE_NONE"))

    private val sampleChatResponse = ChatResponse(
        id = chatId,
        title = "Sprint planning",
        userId = userId,
        projectId = projectId,
        createdAt = OffsetDateTime.now(),
    )

    @Nested
    inner class GetChats {
        @Test
        fun `returns 200 with valid request`() {
            val request = GetChatsRequest(limit = 5, projectId = projectId)
            every {
                chatService.getChatsForCurrentUser(authId, request)
            } returns GetChatsResponse(chats = listOf(sampleChatResponse))

            mockMvc
                .get("/api/v1/chats/me?limit=5&projectId=$projectId") {
                    with(userJwt)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.chats[0].id") { value(chatId.toString()) }
                    jsonPath("$.chats[0].title") { value("Sprint planning") }
                }
        }

        @Test
        fun `returns 200 with null limit (retrieve all)`() {
            val request = GetChatsRequest(limit = null, projectId = projectId)
            val chat = ChatResponse(chatId, "Sprint Planning", userId, projectId, OffsetDateTime.now())
            every {
                chatService.getChatsForCurrentUser(authId, request)
            } returns GetChatsResponse(chats = listOf(chat))

            mockMvc
                .get("/api/v1/chats/me?projectId=$projectId") {
                    with(userJwt)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.chats[0].id") { value(chatId.toString()) }
                    jsonPath("$.chats[0].title") { value("Sprint Planning") }
                }
        }

        @Test
        fun `returns 400 when limit is less than 1`() {
            mockMvc
                .get("/api/v1/chats/me?limit=-5&projectId=$projectId") {
                    with(userJwt)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 401 when not authenticated`() {
            mockMvc
                .get("/api/v1/chats/me?projectId=$projectId")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `returns 403 when authenticated with wrong role`() {
            mockMvc
                .get("/api/v1/chats/me?projectId=$projectId") {
                    with(noUserRoleJwt)
                }.andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        fun `explicit all-chats endpoint rejects normal user`() {
            mockMvc
                .get("/api/v1/chats") {
                    with(userJwt)
                }.andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        fun `explicit all-chats endpoint allows admin`() {
            // The admin listing spans every project on purpose, so it carries no project scope.
            val request = GetChatsRequest(limit = 5)
            every { chatService.getChats(request) } returns GetChatsResponse(chats = listOf(sampleChatResponse))

            mockMvc
                .get("/api/v1/chats?limit=5") {
                    with(adminJwt)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.chats[0].id") { value(chatId.toString()) }
                }
        }
    }

    @Nested
    inner class GetChatMessages {
        @Test
        fun `returns 200 with valid request`() {
            val request = GetChatMessagesRequest(limit = 20)
            every {
                chatService.getChatForCurrentUser(authId, chatId, request)
            } returns GetChatMessagesResponse(
                messages = listOf(ChatMessageResponse(role = ChatRole.USER, content = "Hello")),
            )

            mockMvc
                .get("/api/v1/chats/me/$chatId?limit=20") {
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
            every {
                chatService.getChatForCurrentUser(authId, chatId, request)
            } returns GetChatMessagesResponse(
                messages = listOf(ChatMessageResponse(role = ChatRole.USER, content = "Hello")),
            )

            mockMvc
                .get("/api/v1/chats/me/$chatId") {
                    with(userJwt)
                }.andExpect {
                    status { isOk() }
                    jsonPath("$.messages[0].content") { value("Hello") }
                    jsonPath("$.messages[0].role") { value("USER") }
                }
        }

        @Test
        fun `returns 400 when limit is less than 1`() {
            mockMvc
                .get("/api/v1/chats/me/$chatId?limit=0") {
                    with(userJwt)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 400 when id path variable is not a valid UUID`() {
            mockMvc
                .get("/api/v1/chats/me/not-a-uuid") {
                    with(userJwt)
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `returns 401 when not authenticated`() {
            mockMvc
                .get("/api/v1/chats/me/$chatId")
                .andExpect { status { isUnauthorized() } }
        }

        @Test
        fun `returns 403 when authenticated with wrong role`() {
            mockMvc
                .get("/api/v1/chats/me/$chatId") {
                    with(noUserRoleJwt)
                }.andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        fun `explicit chat messages endpoint rejects normal user`() {
            mockMvc
                .get("/api/v1/chats/$chatId") {
                    with(userJwt)
                }.andExpect {
                    status { isForbidden() }
                }
        }
    }

    @Nested
    inner class CreateChat {
        @Test
        fun `returns 201 with valid request`() {
            every { projectAuth.canAccessProject(any(), projectId) } returns true
            every {
                chatService.createChatForCurrentUser(authId, projectId)
            } returns CreateChatResponse(id = chatId)

            mockMvc
                .post("/api/v1/chats/me") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(CreateMyChatRequest(projectId = projectId))
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.id") { value(chatId.toString()) }
                }
        }

        @Test
        fun `returns 403 when the user has no access to the project`() {
            val foreignProjectId = UUID.randomUUID()
            every { projectAuth.canAccessProject(any(), foreignProjectId) } returns false

            mockMvc
                .post("/api/v1/chats/me") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(CreateMyChatRequest(projectId = foreignProjectId))
                }.andExpect {
                    status { isForbidden() }
                }

            verify(exactly = 0) { chatService.createChatForCurrentUser(any(), any()) }
        }

        @Test
        fun `explicit create endpoint rejects normal user`() {
            mockMvc
                .post("/api/v1/chats") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(CreateChatRequest(userId = userId, projectId = projectId))
                }.andExpect {
                    status { isForbidden() }
                }
        }

        @Test
        fun `explicit create endpoint allows admin`() {
            val request = CreateChatRequest(userId = userId, projectId = projectId)
            every { chatService.createChat(request) } returns CreateChatResponse(id = chatId)

            mockMvc
                .post("/api/v1/chats") {
                    with(adminJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(request)
                }.andExpect {
                    status { isCreated() }
                    jsonPath("$.id") { value(chatId.toString()) }
                }
        }

        @Test
        fun `returns 401 when not authenticated`() {
            mockMvc
                .post("/api/v1/chats/me")
                .andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `returns 403 when authenticated with wrong role`() {
            every { projectAuth.canAccessProject(any(), projectId) } returns true

            mockMvc
                .post("/api/v1/chats/me") {
                    with(noUserRoleJwt)
                    // A valid body is required for the role check to be what rejects the
                    // request — argument resolution runs first, so a missing body would
                    // fail with 400 before authorization is ever evaluated.
                    contentType = MediaType.APPLICATION_JSON
                    content = objectMapper.writeValueAsString(CreateMyChatRequest(projectId = projectId))
                }.andExpect {
                    status { isForbidden() }
                }
        }
    }

    @Nested
    inner class DeleteChat {

        @Test
        fun `returns 204 when admin deletes chat`() {
            every { chatService.deleteChat(chatId) } returns Unit

            mockMvc
                .delete("/api/v1/chats/$chatId") {
                    with(adminJwt)
                }.andExpect {
                    status { isNoContent() }
                }

            verify(exactly = 1) {
                chatService.deleteChat(chatId)
            }
        }

        @Test
        fun `returns 403 when normal user tries to delete chat`() {
            mockMvc
                .delete("/api/v1/chats/$chatId") {
                    with(userJwt)
                }.andExpect {
                    status { isForbidden() }
                }

            verify(exactly = 0) {
                chatService.deleteChat(any())
            }
        }

        @Test
        fun `returns 204 when current user deletes own chat`() {
            every {
                chatService.deleteChatForCurrentUser(authId, chatId)
            } returns Unit

            mockMvc
                .delete("/api/v1/chats/me/$chatId") {
                    with(userJwt)
                }.andExpect {
                    status { isNoContent() }
                }

            verify(exactly = 1) {
                chatService.deleteChatForCurrentUser(authId, chatId)
            }
        }

        @Test
        fun `returns 401 when current user endpoint is called without authentication`() {
            mockMvc
                .delete("/api/v1/chats/me/$chatId")
                .andExpect {
                    status { isUnauthorized() }
                }

            verify(exactly = 0) {
                chatService.deleteChatForCurrentUser(any(), any())
            }
        }

        @Test
        fun `returns 403 when current user endpoint is called with wrong role`() {
            mockMvc
                .delete("/api/v1/chats/me/$chatId") {
                    with(noUserRoleJwt)
                }.andExpect {
                    status { isForbidden() }
                }

            verify(exactly = 0) {
                chatService.deleteChatForCurrentUser(any(), any())
            }
        }
    }

    @Nested
    inner class Prompt {
        @Test
        fun `returns 200 when valid msg`() {
            val messages = listOf(
                AiStreamMessage("token", "The"),
                AiStreamMessage("token", " goal"),
                AiStreamMessage("done"),
            )
            coEvery { chatService.promptForCurrentUser(authId, any()) } returns flowOf(*messages.toTypedArray())

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/chats/me/prompt")
                        .with(userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"chatId": "$chatId", "msg": "Test msg"}"""),
                ).andExpect(request().asyncStarted())
                .andReturn()

            val mvcResult = mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)
                .andReturn()

            val actual = mvcResult.response.contentAsString
                .replace("data:", "")
                .replace("\n", "")

            // The server (de)serializes via kotlinx serialization, which omits null
            // defaults — so we build the expected stream the same way.
            val expected = messages.joinToString("") { Json.encodeToString(it) }

            assertEquals(expected, actual)
        }

        @Test
        fun `forwards tool_use citation and error events untouched`() {
            val messages = listOf(
                AiStreamMessage(type = "tool_use", name = "retrieve", kind = "tool"),
                AiStreamMessage("token", "The main blocker"),
                AiStreamMessage(
                    type = "citation",
                    artifactId = "artifact-1",
                    startLine = 12,
                ),
                AiStreamMessage("done"),
            )
            coEvery { chatService.promptForCurrentUser(authId, any()) } returns flowOf(*messages.toTypedArray())

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/chats/me/prompt")
                        .with(userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"chatId": "$chatId", "msg": "Test msg"}"""),
                ).andExpect(request().asyncStarted())
                .andReturn()

            val mvcResult = mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)
                .andReturn()

            val actual = mvcResult.response.contentAsString
                .replace("data:", "")
                .replace("\n", "")

            val expected = messages.joinToString("") { Json.encodeToString(it) }

            assertEquals(expected, actual)
            // Wire field names must mirror the AI service contract for the frontend.
            assert(actual.contains("""{"type":"tool_use","name":"retrieve","kind":"tool"}"""))
            assert(actual.contains(""""artifact_id":"artifact-1""""))
            assert(actual.contains(""""start_line":12"""))
        }

        @Test
        fun `returns 400 when msg is blank`() {
            mockMvc
                .post("/api/v1/chats/me/prompt") {
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
                .post("/api/v1/chats/me/prompt") {
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
                .post("/api/v1/chats/me/prompt") {
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
                .post("/api/v1/chats/me/prompt") {
                    contentType = MediaType.APPLICATION_JSON
                    content = """{"chatId": "$chatId", "msg": "Hello"}"""
                }.andExpect {
                    status { isUnauthorized() }
                }
        }

        @Test
        fun `returns 403 when authenticated with wrong role`() {
            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/chats/me/prompt")
                        .with(noUserRoleJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"chatId": "$chatId", "msg": "Hello"}"""),
                ).andExpect(request().asyncStarted())
                .andReturn()

            // 2. Dispatch and assert the 403 happens during async execution
            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isForbidden)
        }

        @Test
        fun `rejects duplicated source filters`() {
            mockMvc
                .post("/api/v1/chats/me/prompt") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                            {
                                "chatId": "$chatId",
                                "msg": "Hello",
                                "filters": {
                                    "sourceSystems": [
                                          "GITHUB",
                                          "GITHUB"
                                    ]
                                }
                            }
                        """
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `rejects future filter dates`() {
            mockMvc
                .post("/api/v1/chats/me/prompt") {
                    with(userJwt)
                    contentType = MediaType.APPLICATION_JSON
                    content =
                        """
                            {
                                  "chatId": "$chatId",
                                  "msg": "Hello",
                                  "filters": {
                                        "from": "2050-01-01T00:00:00Z"
                                  }
                            }
                        """
                }.andExpect {
                    status { isBadRequest() }
                }
        }

        @Test
        fun `accepts valid chat filters`() {
            val messages = listOf(
                AiStreamMessage("done"),
            )

            coEvery { chatService.promptForCurrentUser(authId, any()) } returns flowOf(*messages.toTypedArray())

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/chats/me/prompt")
                        .with(userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                            """
                                {
                                      "chatId": "$chatId",
                                      "msg": "Hello",
                                      "filters": {
                                            "sourceSystems": ["GITHUB"],
                                            "from": "2026-01-01T00:00:00Z"
                                      }
                                }
                            """,
                        ),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)

            coVerify {
                chatService.promptForCurrentUser(
                    authId,
                    match {
                        it.filters?.sourceSystems == listOf(SourceSystem.GITHUB) &&
                            it.filters?.from == Instant.parse("2026-01-01T00:00:00Z")
                    },
                )
            }
        }

        @Test
        fun `accepts prompt without filters`() {
            coEvery { chatService.promptForCurrentUser(authId, any()) } returns flowOf(AiStreamMessage("done"))

            val asyncResult = mockMvc
                .perform(
                    post("/api/v1/chats/me/prompt")
                        .with(userJwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"chatId": "$chatId", "msg": "Hello"}"""),
                ).andExpect(request().asyncStarted())
                .andReturn()

            mockMvc
                .perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk)

            coVerify {
                chatService.promptForCurrentUser(
                    authId,
                    match { it.filters == null },
                )
            }
        }
    }
}
