package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySessionFilters
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.SendBuddyMessageRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyActionService
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyService
import com.sprintstart.sprintstartbackend.onboarding.service.BuddySuggestionService
import com.sprintstart.sprintstartbackend.onboarding.service.BuddyTeamService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

/**
 * Team mode is chosen per request by naming a project. These pin that the name reaches the team
 * service, that its absence never does, and that the service's refusal reaches the client as a 403.
 */
@WebMvcTest(BuddyController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class BuddyControllerTeamModeTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var buddyService: BuddyService

    @MockkBean
    private lateinit var buddyTeamService: BuddyTeamService

    @MockkBean
    private lateinit var buddyActionService: BuddyActionService

    @MockkBean
    private lateinit var buddySuggestionService: BuddySuggestionService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val objectMapper = jacksonObjectMapper()
    private val authId = "test-auth-id"
    private val projectId = UUID.randomUUID()

    private val userJwt = jwt()
        .jwt { it.subject(authId).claim("realm_access", mapOf("roles" to listOf("USER", "PM"))) }
        .authorities(listOf(SimpleGrantedAuthority("ROLE_USER"), SimpleGrantedAuthority("ROLE_PM")))

    @Test
    fun `getMessagesForMe with a teamProjectId returns the team conversation`() {
        every { buddyTeamService.getMessagesForMe(authId, projectId) } returns listOf(
            BuddyMessageResponse(
                id = UUID.randomUUID(),
                role = BuddyMessageRole.USER,
                content = "who is stuck?",
                createdAt = Instant.now(),
            ),
        )

        mockMvc
            .perform(
                get("/api/v1/onboarding/me/buddy/messages")
                    .param("teamProjectId", projectId.toString())
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$[0].content").value("who is stuck?"))

        verify(exactly = 0) { buddyService.getMessagesForMe(any(), null) }
    }

    @Test
    fun `getMessagesForMe returns 403 when the caller does not manage the project`() {
        every { buddyTeamService.getMessagesForMe(authId, projectId) } throws
            ResponseStatusException(HttpStatus.FORBIDDEN, "not yours")

        mockMvc
            .perform(
                get("/api/v1/onboarding/me/buddy/messages")
                    .param("teamProjectId", projectId.toString())
                    .with(userJwt),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `streamOpenForMe with a teamProjectId opens team mode`() {
        coEvery { buddyTeamService.streamOpenForMe(authId, projectId) } returns
            flowOf(BuddyStreamEvent(type = "done"))

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/open/stream")
                    .param("teamProjectId", projectId.toString())
                    .with(userJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk)

        coVerify { buddyTeamService.streamOpenForMe(authId, projectId) }
        coVerify(exactly = 0) { buddyService.streamOpenForMe(any(), null) }
    }

    @Test
    fun `sendMessageForMe with a teamProjectId speaks in team mode and keeps the capability mode`() {
        coEvery { buddyTeamService.sendMessageForMe(authId, projectId, "who is stuck?", false) } returns
            flowOf(BuddyStreamEvent(type = "done"))

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/messages")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            SendBuddyMessageRequest(
                                content = "who is stuck?",
                                capabilitiesEnabled = false,
                                teamProjectId = projectId,
                            ),
                        ),
                    ),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk)

        coVerify { buddyTeamService.sendMessageForMe(authId, projectId, "who is stuck?", false) }
        coVerify(exactly = 0) {
            buddyService.sendMessageForMe(
                any(),
                null,
                any(),
                any(),
                null,
            )
        }
    }

    /** The frontend sends snake_case; the team path must read the filters as the hire's path does. */
    @Test
    fun `sendMessageForMe with a teamProjectId passes snake_case filters through`() {
        coEvery {
            buddyTeamService.sendMessageForMe(any(), any(), any(), any(), any(), any())
        } returns flowOf(BuddyStreamEvent(type = "done"))

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/messages")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"content":"who is stuck?","teamProjectId":"$projectId","filters":{
                          "source_systems":["GITHUB","JIRA"],
                          "time_from":"2026-10-01T00:00:00Z",
                          "time_to":"2026-10-06T21:59:59.999Z"}}
                        """.trimIndent(),
                    ),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk)

        val filters = slot<BuddySessionFilters>()
        coVerify {
            buddyTeamService.sendMessageForMe(authId, projectId, "who is stuck?", true, capture(filters), null)
        }
        assertThat(filters.captured.sourceSystems).containsExactly(SourceSystem.GITHUB, SourceSystem.JIRA)
        assertThat(filters.captured.from).isEqualTo("2026-10-01T00:00:00Z")
        assertThat(filters.captured.to).isEqualTo("2026-10-06T21:59:59.999Z")
    }

    /** A client that never names a project stays in the caller's own buddy, exactly as before. */
    @Test
    fun `sendMessageForMe without a teamProjectId never reaches team mode`() {
        coEvery {
            buddyService.sendMessageForMe(
                authId,
                null,
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
                    .content("""{"content":"hi"}"""),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk)

        coVerify(exactly = 0) { buddyTeamService.sendMessageForMe(any(), any(), any(), any()) }
    }

    @Test
    fun `sendMessageForMe returns 403 when the caller does not manage the project`() {
        coEvery { buddyTeamService.sendMessageForMe(authId, projectId, "who is stuck?", true) } throws
            ResponseStatusException(HttpStatus.FORBIDDEN, "not yours")

        val asyncResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/buddy/messages")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            SendBuddyMessageRequest(content = "who is stuck?", teamProjectId = projectId),
                        ),
                    ),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isForbidden)
    }
}
