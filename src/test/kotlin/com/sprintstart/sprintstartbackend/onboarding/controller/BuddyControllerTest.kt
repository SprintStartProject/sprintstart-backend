package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.BuddyActionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.SendBuddyMessageRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyActionResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddySuggestionResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.CreateSessionResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyActionService
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyService
import com.sprintstart.sprintstartbackend.onboarding.service.BuddySuggestionService
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyTeamService
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
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
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(BuddyController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class BuddyControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var buddyService: BuddyService

    // Never called by these tests — the controller needs it to start. Team routing is
    // BuddyControllerTeamModeTest's.
    @MockkBean
    private lateinit var buddyTeamService: BuddyTeamService

    @MockkBean
    private lateinit var buddyActionService: BuddyActionService

    @MockkBean
    private lateinit var buddySuggestionService: BuddySuggestionService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuth: ProjectAuthorization

    private val objectMapper = jacksonObjectMapper()
    private val authId = "test-auth-id"

    private fun jwtWithRoles(vararg roles: String): JwtRequestPostProcessor =
        jwt()
            .jwt { jwt ->
                jwt.subject(authId)
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    private val userJwt = jwtWithRoles("USER")
    private val noUserRoleJwt = jwtWithRoles("PM")

    @Test
    fun `getMessagesForMe should return 200 with the conversation`() {
        val sessionId = UUID.randomUUID()

        every {
            buddyService.getMessagesForMe(authId, sessionId)
        } returns listOf(
            BuddyMessageResponse(
                role = BuddyMessageRole.USER,
                content = "Hi",
                createdAt = Instant.now(),
            ),
        )

        mockMvc
            .perform(
                get("/api/v1/onboarding/me/buddy/messages")
                    .param("sessionId", sessionId.toString())
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].content").value("Hi"))

        verify { buddyService.getMessagesForMe(authId, sessionId) }
    }

    @Test
    fun `getMessagesForMe without a sessionId should return 400`() {
        every {
            buddyService.getMessagesForMe(authId, null)
        } throws ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "sessionId required",
        )

        mockMvc
            .perform(
                get("/api/v1/onboarding/me/buddy/messages")
                    .with(userJwt),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `getMessagesForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/buddy/messages"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getSuggestionsForMe should return 200 with the hire's chips`() {
        every { buddySuggestionService.forMe(authId) } returns listOf(
            BuddySuggestionResponse(
                label = "Anything I can pick up?",
                question = "Is there something in the work pool I could pick up?",
            ),
        )

        mockMvc
            .perform(get("/api/v1/onboarding/me/buddy/suggestions").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].label").value("Anything I can pick up?"))
            .andExpect(jsonPath("$[0].question").value("Is there something in the work pool I could pick up?"))
    }

    /**
     * A plain (non-suspend) handler, so a single-step expectation is enough here — unlike the
     * suspend endpoints below, where a naive `andExpect(status())` silently passes a role denial.
     */
    @Test
    fun `getSuggestionsForMe should return 403 for a non-USER role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/buddy/suggestions").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `creates session for accessible project`() {
        val projectId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()

        every {
            projectAuth.canAccessProject(any(), projectId)
        } returns true

        every {
            buddyService.createSession(authId, projectId)
        } returns CreateSessionResponse(sessionId)

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectId":"$projectId"}""")
                    .with(userJwt),
            ).andExpect(status().isCreated)
    }

    @Test
    fun `rejects session creation for inaccessible project`() {
        val projectId = UUID.randomUUID()

        every {
            projectAuth.canAccessProject(any(), projectId)
        } returns false

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"projectId":"$projectId"}""")
                    .with(userJwt),
            ).andExpect(status().isForbidden)

        verify(exactly = 0) {
            buddyService.createSession(any(), any())
        }
    }

    @Test
    fun `creates unscoped session without checking project access`() {
        val sessionId = UUID.randomUUID()

        every {
            buddyService.createSession(authId, null)
        } returns CreateSessionResponse(sessionId)

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/sessions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
                    .with(userJwt),
            ).andExpect(status().isCreated)

        verify(exactly = 0) {
            projectAuth.canAccessProject(any(), any())
        }
    }

    @Test
    fun `streamOpenForMe should stream the greeting and its suggested next step`() {
        val events = listOf(
            BuddyStreamEvent(type = "token", content = "Welcome "),
            BuddyStreamEvent(type = "token", content = "back, Sam!"),
            BuddyStreamEvent(type = "opening_action", label = "Find me a task", question = "What next?"),
            BuddyStreamEvent(type = "done"),
        )
        val sessionId = UUID.randomUUID()

        coEvery {
            buddyService.streamOpenForMe(authId, sessionId)
        } returns flowOf(*events.toTypedArray())

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/open/stream")
                    .param("sessionId", sessionId.toString())
                    .with(userJwt),
            ).andReturn()

        val mvcResult = mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andReturn()

        val actual = mvcResult.response.contentAsString
            .replace("data:", "")
            .replace("\n", "")
        assertEquals(events.joinToString("") { Json.encodeToString(it) }, actual)
    }

    @Test
    fun `streamOpenForMe without a sessionId should return 400`() {
        coEvery {
            buddyService.streamOpenForMe(authId, null)
        } throws ResponseStatusException(
            HttpStatus.BAD_REQUEST,
            "sessionId required",
        )

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/open/stream")
                    .with(userJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isBadRequest)
    }

    /**
     * A naive single-step `.andExpect(status()...)` passes through role denials on a suspend
     * handler, because Spring dispatches it asynchronously. The two-step form is what actually
     * asserts the 403.
     */
    @Test
    fun `streamOpenForMe should return 403 for a non-USER role`() {
        val asyncResult = mockMvc
            .perform(post("/api/v1/onboarding/me/buddy/open/stream").with(noUserRoleJwt))
            .andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `streamOpenForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(post("/api/v1/onboarding/me/buddy/open/stream"))
            .andExpect(status().isUnauthorized)
    }

    /**
     * The switch is the visible half of "just let me look something up", so it has to survive the
     * wire. A request that silently lost it would mount the full mentor for a hire who asked for
     * the corpus.
     */
    @Test
    fun `sendMessageForMe passes the capability mode through`() {
        val sessionId = UUID.randomUUID()

        coEvery {
            buddyService.sendMessageForMe(
                authId,
                sessionId,
                "where are the deploy docs?",
                false,
                null,
            )
        } returns flowOf(BuddyStreamEvent(type = "done"))

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/messages")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            SendBuddyMessageRequest(
                                content = "where are the deploy docs?",
                                sessionId = sessionId,
                                capabilitiesEnabled = false,
                            ),
                        ),
                    ),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk)

        coVerify {
            buddyService.sendMessageForMe(
                authId,
                sessionId,
                "where are the deploy docs?",
                false,
                null,
            )
        }
    }

    /** A client that has never heard of the switch gets the full mentor, as it always did. */
    @Test
    fun `sendMessageForMe defaults to the full mentor`() {
        val sessionId = UUID.randomUUID()

        coEvery {
            buddyService.sendMessageForMe(
                authId,
                sessionId,
                "hi",
                true,
                null,
            )
        } returns flowOf(BuddyStreamEvent(type = "done"))

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/messages")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            SendBuddyMessageRequest(
                                content = "hi",
                                sessionId = sessionId,
                            ),
                        ),
                    ),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)

        coVerify {
            buddyService.sendMessageForMe(
                authId,
                sessionId,
                "hi",
                true,
                null,
            )
        }
    }

    /** The page the hire was on reaches the buddy, so "where is this here?" has a "here". */
    @Test
    fun `sendMessageForMe passes the current page through`() {
        val sessionId = UUID.randomUUID()
        coEvery {
            buddyService.sendMessageForMe(
                authId,
                sessionId,
                "where are roles?",
                true,
                null,
                "/team-management",
            )
        } returns
            flowOf(BuddyStreamEvent(type = "done"))

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/messages")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"sessionId":"$sessionId","content":"where are roles?","currentPage":"/team-management"}""",
                    ),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk)

        coVerify {
            buddyService.sendMessageForMe(
                authId,
                sessionId,
                "where are roles?",
                true,
                null,
                "/team-management",
            )
        }
    }

    @Test
    fun `sendMessageForMe should stream tokens and done`() {
        val events = listOf(
            BuddyStreamEvent(type = "token", content = "No question "),
            BuddyStreamEvent(type = "token", content = "is too basic."),
            BuddyStreamEvent(type = "done"),
        )
        val sessionId = UUID.randomUUID()

        coEvery {
            buddyService.sendMessageForMe(
                authId,
                sessionId,
                "How do I get set up?",
                true,
                null,
            )
        } returns flowOf(*events.toTypedArray())

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/messages")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            SendBuddyMessageRequest(sessionId, "How do I get set up?"),
                        ),
                    ),
            ).andExpect(request().asyncStarted())
            .andReturn()

        val mvcResult = mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andReturn()

        val actual = mvcResult.response.contentAsString
            .replace("data:", "")
            .replace("\n", "")
        val expected = events.joinToString("") { Json.encodeToString(it) }

        assertEquals(expected, actual)
    }

    @Test
    fun `sendMessageForMe should return 403 for a non-USER role`() {
        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/messages")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            SendBuddyMessageRequest(null, "Hi"),
                        ),
                    ),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `sendMessageForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            SendBuddyMessageRequest(null, "Hi"),
                        ),
                    ),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `performAction should return 200 with the outcome`() {
        coEvery { buddyActionService.perform(any(), any()) } returns
            BuddyActionResponse(ok = true, message = "You are now working toward it.")

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/actions")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(BuddyActionRequest(action = "claim_goal"))),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.message").value("You are now working toward it."))
    }

    @Test
    fun `performAction should return 403 for a non-USER role`() {
        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/actions")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(BuddyActionRequest(action = "claim_goal"))),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isForbidden)
    }
}
