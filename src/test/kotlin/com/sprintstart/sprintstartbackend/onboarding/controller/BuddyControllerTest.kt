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
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyActionService
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyService
import com.sprintstart.sprintstartbackend.onboarding.service.BuddySuggestionService
import io.mockk.coEvery
import io.mockk.every
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(BuddyController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class BuddyControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var buddyService: BuddyService

    @MockkBean
    private lateinit var buddyActionService: BuddyActionService

    @MockkBean
    private lateinit var buddySuggestionService: BuddySuggestionService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

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
        every { buddyService.getMessagesForMe(authId) } returns listOf(
            BuddyMessageResponse(role = BuddyMessageRole.USER, content = "Hi", createdAt = Instant.now()),
        )

        mockMvc
            .perform(get("/api/v1/onboarding/me/buddy/messages").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].content").value("Hi"))
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
            BuddySuggestionResponse(label = "What should I work on?", question = "What should I work on next?"),
        )

        mockMvc
            .perform(get("/api/v1/onboarding/me/buddy/suggestions").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].label").value("What should I work on?"))
            .andExpect(jsonPath("$[0].question").value("What should I work on next?"))
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
    fun `streamOpenForMe should stream the greeting and its suggested next step`() {
        val events = listOf(
            BuddyStreamEvent(type = "token", content = "Welcome "),
            BuddyStreamEvent(type = "token", content = "back, Sam!"),
            BuddyStreamEvent(type = "opening_action", label = "Find me a task", question = "What next?"),
            BuddyStreamEvent(type = "done"),
        )
        coEvery { buddyService.streamOpenForMe(authId) } returns flowOf(*events.toTypedArray())

        val asyncResult = mockMvc
            .perform(post("/api/v1/onboarding/me/buddy/open/stream").with(userJwt))
            .andExpect(request().asyncStarted())
            .andReturn()

        val mvcResult = mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andReturn()

        val actual = mvcResult.response.contentAsString
            .replace("data:", "")
            .replace("\n", "")
        assertEquals(events.joinToString("") { Json.encodeToString(it) }, actual)
    }

    /**
     * ⚠️ A naive single-step `.andExpect(status()...)` passes through role denials on a suspend
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

    @Test
    fun `sendMessageForMe should stream tokens and done`() {
        val events = listOf(
            BuddyStreamEvent(type = "token", content = "No question "),
            BuddyStreamEvent(type = "token", content = "is too basic."),
            BuddyStreamEvent(type = "done"),
        )
        coEvery { buddyService.sendMessageForMe(authId, "How do I get set up?") } returns flowOf(*events.toTypedArray())

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/messages")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(SendBuddyMessageRequest("How do I get set up?"))),
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
                    .content(objectMapper.writeValueAsString(SendBuddyMessageRequest("Hi"))),
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
                    .content(objectMapper.writeValueAsString(SendBuddyMessageRequest("Hi"))),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `performAction should return 200 with the outcome`() {
        coEvery { buddyActionService.perform(any(), any()) } returns
            BuddyActionResponse(ok = true, message = "Task 0 is yours.")

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/actions")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(BuddyActionRequest(action = "claim_task_zero"))),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.message").value("Task 0 is yours."))
    }

    @Test
    fun `performAction should return 403 for a non-USER role`() {
        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/actions")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(BuddyActionRequest(action = "claim_task_zero"))),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(asyncResult))
            .andExpect(status().isForbidden)
    }
}
