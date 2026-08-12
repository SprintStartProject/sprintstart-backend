package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.model.request.assessment.AnswerAssessmentRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.assessment.AnswerAssessmentResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.assessment.StartAssessmentResponse
import com.sprintstart.sprintstartbackend.onboarding.service.AssessmentService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * Controller handlers here are `suspend fun` (the service makes a real AI call), so Spring MVC
 * dispatches them asynchronously. MockMvc only sees the committed response after replaying the
 * async result via [asyncDispatch] -- asserting directly on the initial `perform()` result (as
 * for a synchronous controller) sees an empty, not-yet-committed response.
 */
@WebMvcTest(AssessmentController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class AssessmentControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var assessmentService: AssessmentService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val authId = "test-auth-id"
    private val sessionId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    private fun jwtWithSubject(subject: String, vararg roles: String): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { role -> SimpleGrantedAuthority("ROLE_$role") })
    }

    private val userJwt = jwtWithSubject(authId, "USER")
    private val noUserRoleJwt = jwtWithSubject(authId, "NONE")

    // ========================== /me/assessment/status ==========================

    // Non-suspend handler, so no async dispatch is involved here.
    @Test
    fun `getAssessmentStatusForMe should return 200 with the completion flag`() {
        every { assessmentService.hasCompletedAssessment(authId, projectId) } returns true

        mockMvc
            .perform(
                get("/api/v1/onboarding/me/assessment/status")
                    .queryParam("projectId", projectId.toString())
                    .with(userJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.completed").value(true))
    }

    @Test
    fun `getAssessmentStatusForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/assessment/status").queryParam("projectId", projectId.toString()))
            .andExpect(status().isUnauthorized)
    }

    // ========================== /me/assessment/start ==========================

    @Test
    fun `startAssessmentForMe should return 200 and the first question`() {
        coEvery { assessmentService.startAssessment(authId, projectId) } returns
            StartAssessmentResponse(sessionId = sessionId, question = "Walk me through your last PR.")

        val mvcResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/assessment/start")
                    .queryParam("projectId", projectId.toString())
                    .with(userJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.sessionId").value(sessionId.toString()))
            .andExpect(jsonPath("$.question").value("Walk me through your last PR."))

        coVerify(exactly = 1) { assessmentService.startAssessment(authId, projectId) }
    }

    @Test
    fun `startAssessmentForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(post("/api/v1/onboarding/me/assessment/start").queryParam("projectId", projectId.toString()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `startAssessmentForMe should return 403 when authenticated with wrong role`() {
        val mvcResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/assessment/start")
                    .queryParam("projectId", projectId.toString())
                    .with(noUserRoleJwt),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(mvcResult))
            .andExpect(status().isForbidden)
    }

    // ========================== /me/assessment/answer ==========================

    @Test
    fun `answerAssessmentForMe should return 200 and the next question when not done`() {
        val request = AnswerAssessmentRequest(sessionId = sessionId, answer = "It fixed a null-pointer bug.")
        coEvery { assessmentService.answerAssessment(authId, sessionId, request.answer) } returns
            AnswerAssessmentResponse(done = false, question = "What would you do differently?")

        val mvcResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/assessment/answer")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.done").value(false))
            .andExpect(jsonPath("$.question").value("What would you do differently?"))

        coVerify(exactly = 1) { assessmentService.answerAssessment(authId, sessionId, request.answer) }
    }

    @Test
    fun `answerAssessmentForMe should return 200 and done true with no question when finished`() {
        val request = AnswerAssessmentRequest(sessionId = sessionId, answer = "final answer")
        coEvery { assessmentService.answerAssessment(authId, sessionId, request.answer) } returns
            AnswerAssessmentResponse(done = true, question = null)

        val mvcResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/assessment/answer")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.done").value(true))
            .andExpect(jsonPath("$.question").doesNotExist())
    }

    @Test
    fun `answerAssessmentForMe should return 400 for a blank answer`() {
        val request = AnswerAssessmentRequest(sessionId = sessionId, answer = "   ")

        // Bean validation fails before the handler is invoked, so this stays synchronous.
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/assessment/answer")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `answerAssessmentForMe should return 403 when authenticated with wrong role`() {
        val request = AnswerAssessmentRequest(sessionId = sessionId, answer = "answer")

        val mvcResult = mockMvc
            .perform(
                post("/api/v1/onboarding/me/assessment/answer")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(request().asyncStarted())
            .andReturn()

        mockMvc
            .perform(asyncDispatch(mvcResult))
            .andExpect(status().isForbidden)
    }
}
