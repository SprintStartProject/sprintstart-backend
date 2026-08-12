package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireTimelineResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.metrics.HireVocabularyResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingMetricsService
import com.sprintstart.sprintstartbackend.onboarding.service.ProjectAttentionService
import com.sprintstart.sprintstartbackend.user.external.UserApi
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.Optional
import java.util.UUID

@WebMvcTest(OnboardingMetricsController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class OnboardingMetricsControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var onboardingMetricsService: OnboardingMetricsService

    @MockkBean
    private lateinit var projectAttentionService: ProjectAttentionService

    @MockkBean
    private lateinit var userApi: UserApi

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val authId = "test-auth-id"
    private val userId = UUID.randomUUID()
    private val projectId = UUID.randomUUID()

    private fun jwtWithSubject(
        subject: String,
        vararg roles: String,
    ): JwtRequestPostProcessor =
        jwt()
            .jwt { jwt ->
                jwt.subject(subject)
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { role -> SimpleGrantedAuthority("ROLE_$role") })

    private val userJwt = jwtWithSubject(authId, "USER")
    private val noUserRoleJwt = jwtWithSubject(authId, "NONE")

    private fun timeline() =
        HireTimelineResponse(
            userId = userId,
            displayName = "Ada",
            githubLogin = "ada",
            joinedAt = null,
            taskZeroAssignedAt = null,
            firstTaskClaimedAt = null,
            firstContributionOpenedAt = null,
            firstResponseAt = null,
            firstContributionAcceptedAt = null,
            hoursToFirstAcceptedContribution = null,
            hoursToFirstResponse = null,
            acceptedContributionCount = 0,
            openContributionCount = 1,
            longestOpenWaitHours = 72,
            stalled = true,
            stalledReason = "Waiting on a review for 3 days",
            autonomyReachedAt = null,
            vocabulary = HireVocabularyResponse(
                trackLabel = "Engineering",
                contributionNoun = "change",
                contributionNounPlural = "changes",
                contributionVerbPast = "merged",
            ),
            returnedContributionCount = 0,
        )

    @Test
    fun `getMyTimeline should resolve the caller and return their own timeline`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { onboardingMetricsService.getHireTimeline(userId, projectId) } returns timeline()

        mockMvc
            .perform(get("/api/v1/onboarding/metrics/me").param("projectId", projectId.toString()).with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            // The number the whole endpoint exists for: how long their PR has waited.
            .andExpect(jsonPath("$.longestOpenWaitHours").value(72))
            .andExpect(jsonPath("$.stalled").value(true))

        verify(exactly = 1) { onboardingMetricsService.getHireTimeline(userId, projectId) }
    }

    @Test
    fun `getMyTimeline should return 404 when the caller is not a member of the project`() {
        every { userApi.getUserIdByAuthId(authId) } returns Optional.of(userId)
        every { onboardingMetricsService.getHireTimeline(userId, projectId) } returns null

        mockMvc
            .perform(get("/api/v1/onboarding/metrics/me").param("projectId", projectId.toString()).with(userJwt))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getMyTimeline should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/metrics/me").param("projectId", projectId.toString()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getMyTimeline should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/metrics/me").param("projectId", projectId.toString()).with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }
}
