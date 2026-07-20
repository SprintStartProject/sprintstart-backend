package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.SubmitCheckAnswerRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.SubmitPhaseCheckAttemptRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.UpdateCheckQuestionRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.check.UpdatePhaseCheckRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.CheckAnswerResultResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.CheckQuestionForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckAttemptsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckForUserResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.GetPhaseCheckResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.PhaseCheckSummaryResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.check.SubmitPhaseCheckAttemptResponse
import com.sprintstart.sprintstartbackend.onboarding.service.PhaseCheckService
import io.mockk.every
import io.mockk.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(PhaseCheckController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class PhaseCheckControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var phaseCheckService: PhaseCheckService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val authId = "test-auth-id"
    private val adminAuthId = "test-admin-auth-id"
    private val phaseId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val questionId = UUID.randomUUID()

    private fun jwtWithSubject(subject: String, vararg roles: String): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { role -> SimpleGrantedAuthority("ROLE_$role") })
    }

    private val userJwt = jwtWithSubject(authId, "USER")
    private val adminJwt = jwtWithSubject(adminAuthId, "USER", "ADMIN")
    private val noUserRoleJwt = jwtWithSubject(authId, "NONE")

    private fun summary() = PhaseCheckSummaryResponse(
        required = true,
        questionCount = 1,
        passed = false,
        latestAttemptId = null,
        latestAttemptAt = null,
    )

    // ========================== /me endpoints ==========================

    @Test
    fun `getPhaseCheckForMe should return 200 and check without answers`() {
        val response = GetPhaseCheckForUserResponse(
            phaseId = phaseId,
            required = true,
            passed = false,
            latestAttemptId = null,
            questions = listOf(
                CheckQuestionForUserResponse(
                    id = questionId,
                    position = 0,
                    type = CheckQuestionType.SHORT_TEXT,
                    question = "cmd?",
                ),
            ),
        )
        every { phaseCheckService.getPhaseCheckForMe(authId, phaseId) } returns response

        mockMvc
            .perform(get("/api/v1/onboarding/me/phases/$phaseId/checks").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.required").value(true))
            .andExpect(jsonPath("$.questions[0].question").value("cmd?"))

        verify(exactly = 1) { phaseCheckService.getPhaseCheckForMe(authId, phaseId) }
    }

    @Test
    fun `getPhaseCheckForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/phases/$phaseId/checks"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getPhaseCheckForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/phases/$phaseId/checks").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `submitPhaseCheckAttemptForMe should return 201 and grading result`() {
        val request = SubmitPhaseCheckAttemptRequest(
            answers = listOf(SubmitCheckAnswerRequest(questionId = questionId, textAnswer = "run")),
        )
        val response = SubmitPhaseCheckAttemptResponse(
            attemptId = UUID.randomUUID(),
            phaseId = phaseId,
            passed = true,
            createdAt = Instant.parse("2026-07-02T16:00:00Z"),
            correctCount = 1,
            questionCount = 1,
            requiredPercent = 80,
            phaseCheckSummary = summary().copy(passed = true),
            nextPhaseUnlocked = true,
            results = listOf(
                CheckAnswerResultResponse(
                    questionId = questionId,
                    correct = true,
                    correctAnswer = "run",
                ),
            ),
        )
        every { phaseCheckService.submitPhaseCheckAttemptForMe(authId, phaseId, request) } returns response

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/phases/$phaseId/checks/attempts")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.passed").value(true))
            .andExpect(jsonPath("$.nextPhaseUnlocked").value(true))
            .andExpect(jsonPath("$.results[0].correct").value(true))

        verify(exactly = 1) { phaseCheckService.submitPhaseCheckAttemptForMe(authId, phaseId, request) }
    }

    @Test
    fun `submitPhaseCheckAttemptForMe should return 403 when authenticated with wrong role`() {
        val request = SubmitPhaseCheckAttemptRequest()

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/phases/$phaseId/checks/attempts")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isForbidden)
    }

    // ========================== admin endpoints ==========================

    @Test
    fun `getPhaseCheck should return 200 for admins`() {
        every { phaseCheckService.getPhaseCheck(phaseId) } returns GetPhaseCheckResponse(phaseId, emptyList())

        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId/checks").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { phaseCheckService.getPhaseCheck(phaseId) }
    }

    @Test
    fun `getPhaseCheck should return 403 for a plain user`() {
        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId/checks").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `replacePhaseCheck should return 200 for admins`() {
        val request = UpdatePhaseCheckRequest(
            questions = listOf(
                UpdateCheckQuestionRequest(
                    position = 0,
                    type = CheckQuestionType.SHORT_TEXT,
                    question = "cmd?",
                    correctAnswer = "run",
                ),
            ),
        )
        every { phaseCheckService.replacePhaseCheck(phaseId, request) } returns
            GetPhaseCheckResponse(phaseId, emptyList())

        mockMvc
            .perform(
                put("/api/v1/onboarding/phases/$phaseId/checks")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)

        verify(exactly = 1) { phaseCheckService.replacePhaseCheck(phaseId, request) }
    }

    @Test
    fun `replacePhaseCheck should propagate 400 from the service`() {
        val request = UpdatePhaseCheckRequest(
            questions = listOf(
                UpdateCheckQuestionRequest(position = 0, type = CheckQuestionType.SHORT_TEXT, question = "q"),
            ),
        )
        every { phaseCheckService.replacePhaseCheck(phaseId, request) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST)

        mockMvc
            .perform(
                put("/api/v1/onboarding/phases/$phaseId/checks")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `getPhaseCheckAttemptsForUser should return 200 for admins`() {
        every { phaseCheckService.getPhaseCheckAttemptsForUser(userId, phaseId) } returns
            GetPhaseCheckAttemptsResponse(userId, phaseId, emptyList())

        mockMvc
            .perform(get("/api/v1/onboarding/users/$userId/phases/$phaseId/checks/attempts").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { phaseCheckService.getPhaseCheckAttemptsForUser(userId, phaseId) }
    }

    @Test
    fun `getPhaseCheckAttemptsForUser should return 403 for a plain user`() {
        mockMvc
            .perform(get("/api/v1/onboarding/users/$userId/phases/$phaseId/checks/attempts").with(userJwt))
            .andExpect(status().isForbidden)
    }
}
