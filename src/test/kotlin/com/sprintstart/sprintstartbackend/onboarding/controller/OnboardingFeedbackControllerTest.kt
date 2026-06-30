package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.model.request.feedback.CreateOnboardingFeedbackRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetAdminOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.GetOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.feedback.ReadOnboardingFeedbackResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingFeedbackService
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(OnboardingFeedbackController::class, OnboardingFeedbackAdminController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class OnboardingFeedbackControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var onboardingFeedbackService: OnboardingFeedbackService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val stepId = UUID.randomUUID()
    private val feedbackId = UUID.randomUUID()
    private val userId = UUID.randomUUID()

    private val authId = "test-auth-id"
    private val adminAuthId = "test-admin-auth-id"

    private fun jwtWithSubject(
        subject: String,
        vararg roles: String,
    ): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim(
                    "realm_access",
                    mapOf("roles" to roles.toList()),
                )
            }.authorities(
                roles.map { role -> SimpleGrantedAuthority("ROLE_$role") },
            )
    }

    private val userJwt = jwtWithSubject(authId, "USER")
    private val adminJwt = jwtWithSubject(adminAuthId, "USER", "ADMIN")
    private val noUserRoleJwt = jwtWithSubject(authId, "NONE")

    private fun buildGetFeedbackResponse() = GetOnboardingFeedbackResponse(
        id = feedbackId,
        stepId = stepId,
        helpful = true,
        comment = "Great step!",
        createdAt = Instant.now(),
    )

    private fun buildGetAdminFeedbackResponse() = GetAdminOnboardingFeedbackResponse(
        id = feedbackId,
        userId = userId,
        stepId = stepId,
        stepTitle = "Step",
        message = "Great step!",
        read = false,
        createdAt = Instant.now(),
    )

    private fun buildReadFeedbackResponse() = ReadOnboardingFeedbackResponse(
        id = feedbackId,
        read = true,
    )

    private fun buildCreateRequest() = CreateOnboardingFeedbackRequest(
        stepId = stepId,
        helpful = true,
        message = "Great step!",
    )

    // ========================== User endpoints ==========================

    @Test
    fun `getAllFeedbackForMe should return 200 and list`() {
        every { onboardingFeedbackService.getAllFeedbackForMe(authId) } returns listOf(buildGetFeedbackResponse())

        mockMvc
            .perform(get("/api/v1/onboarding/me/feedback").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        io.mockk.verify(exactly = 1) { onboardingFeedbackService.getAllFeedbackForMe(authId) }
    }

    @Test
    fun `getAllFeedbackForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/feedback"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getAllFeedbackForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/feedback").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getFeedbackByStepIdForMe should return 200 and list`() {
        every { onboardingFeedbackService.getFeedbackByStepIdForMe(authId, stepId) } returns
            listOf(buildGetFeedbackResponse())

        mockMvc
            .perform(get("/api/v1/onboarding/me/steps/$stepId/feedback").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        io.mockk.verify(exactly = 1) { onboardingFeedbackService.getFeedbackByStepIdForMe(authId, stepId) }
    }

    @Test
    fun `getFeedbackByStepIdForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/steps/$stepId/feedback"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getFeedbackByStepIdForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/steps/$stepId/feedback").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createFeedbackForMe should return 201 and created feedback`() {
        val request = buildCreateRequest()
        every { onboardingFeedbackService.createFeedbackForMe(authId, request) } returns buildGetFeedbackResponse()

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/feedback")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        io.mockk.verify(exactly = 1) { onboardingFeedbackService.createFeedbackForMe(authId, request) }
    }

    @Test
    fun `createFeedbackForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/feedback")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `createFeedbackForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/feedback")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isForbidden)
    }

    // ========================== Admin endpoints ==========================

    @Test
    fun `getAllFeedback should return 200 and list`() {
        every { onboardingFeedbackService.getAllFeedback() } returns listOf(buildGetAdminFeedbackResponse())

        mockMvc
            .perform(get("/api/v1/admin/onboarding/feedback").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        io.mockk.verify(exactly = 1) { onboardingFeedbackService.getAllFeedback() }
    }

    @Test
    fun `getAllFeedback should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/admin/onboarding/feedback"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getAllFeedback should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/admin/onboarding/feedback").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getAllFeedbackByUserId should return 200 and list`() {
        every { onboardingFeedbackService.getAllFeedbackByUserId(userId) } returns
            listOf(buildGetAdminFeedbackResponse())

        mockMvc
            .perform(get("/api/v1/admin/onboarding/users/$userId/feedback").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        io.mockk.verify(exactly = 1) { onboardingFeedbackService.getAllFeedbackByUserId(userId) }
    }

    @Test
    fun `getAllFeedbackByUserId should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/admin/onboarding/users/$userId/feedback"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getAllFeedbackByUserId should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/admin/onboarding/users/$userId/feedback").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getAllFeedbackByStepId should return 200 and list`() {
        every { onboardingFeedbackService.getAllFeedbackByStepId(stepId) } returns
            listOf(buildGetAdminFeedbackResponse())

        mockMvc
            .perform(get("/api/v1/admin/onboarding/steps/$stepId/feedback").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        io.mockk.verify(exactly = 1) { onboardingFeedbackService.getAllFeedbackByStepId(stepId) }
    }

    @Test
    fun `getAllFeedbackByStepId should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/admin/onboarding/steps/$stepId/feedback"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getAllFeedbackByStepId should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/admin/onboarding/steps/$stepId/feedback").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `markFeedbackAsRead should return 200 and read response`() {
        every { onboardingFeedbackService.markFeedbackAsRead(feedbackId) } returns buildReadFeedbackResponse()

        mockMvc
            .perform(post("/api/v1/admin/onboarding/feedback/$feedbackId/read").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        io.mockk.verify(exactly = 1) { onboardingFeedbackService.markFeedbackAsRead(feedbackId) }
    }

    @Test
    fun `markFeedbackAsRead should return 401 when not authenticated`() {
        mockMvc
            .perform(post("/api/v1/admin/onboarding/feedback/$feedbackId/read"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `markFeedbackAsRead should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(post("/api/v1/admin/onboarding/feedback/$feedbackId/read").with(userJwt))
            .andExpect(status().isForbidden)
    }
}
