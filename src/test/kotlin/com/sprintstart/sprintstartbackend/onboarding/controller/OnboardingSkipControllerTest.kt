package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkipStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.CreateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.ReviewOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.skip.UpdateOnboardingSkipRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.CreateOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetAllOnboardingSkipsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.GetOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.skip.ReviewOnboardingSkipResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingSkipService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@WebMvcTest(
    OnboardingSkipController::class,
    OnboardingSkipAdminController::class,
)
@Import(
    SecurityConfig::class,
)
@AutoConfigureMockMvc
class OnboardingSkipControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var onboardingSkipService: OnboardingSkipService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val stepId = UUID.randomUUID()
    private val skipId = UUID.randomUUID()
    private val userId = UUID.randomUUID()
    private val authId = "test-auth-id"
    private val adminAuthId = "test-admin-auth-id"
    private val timestamp = Instant.parse("2026-06-23T09:00:00Z")

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

    private fun buildCreateRequest() = CreateOnboardingSkipRequest(
        reason = "Need to skip this step",
    )

    private fun buildUpdateRequest() = UpdateOnboardingSkipRequest(
        reason = "Updated reason",
    )

    private fun buildReviewRequest() = ReviewOnboardingSkipRequest(
        reviewComment = "Approved by admin",
    )

    private fun buildGetSkipResponse() = GetOnboardingSkipResponse(
        id = skipId,
        stepId = stepId,
        status = SkipStatus.PENDING,
        reason = "Need to skip this step",
        reviewComment = null,
        createdAt = timestamp,
        resolvedAt = null,
    )

    private fun buildGetAllSkipResponse() = GetAllOnboardingSkipsResponse(
        id = skipId,
        stepId = stepId,
        status = SkipStatus.PENDING,
        reason = "Need to skip this step",
        reviewComment = null,
        createdAt = timestamp,
        resolvedAt = null,
    )

    private fun buildCreateSkipResponse() = CreateOnboardingSkipResponse(
        id = skipId,
        stepId = stepId,
        status = SkipStatus.PENDING,
        reason = "Need to skip this step",
        reviewComment = null,
        createdAt = timestamp,
        reviewedAt = null,
    )

    private fun buildUpdateStepResponse() = UpdateOnboardingStepResponse(
        id = stepId,
        phaseId = UUID.randomUUID(),
        position = 1,
        title = "Step 1",
        description = "desc",
        estimatedMinutes = 15,
        expectedOutcome = "Outcome",
        status = StepStatus.WAITING,
        completedAt = null,
        skip = GetOnboardingSkipResponse(
            id = skipId,
            stepId = stepId,
            status = SkipStatus.PENDING,
            reason = "Updated reason",
            reviewComment = null,
            createdAt = timestamp,
            resolvedAt = null,
        ),
    )

    private fun buildReviewResponse(status: SkipStatus) = ReviewOnboardingSkipResponse(
        id = skipId,
        stepId = stepId,
        status = status,
        reason = "Need to skip this step",
        reviewComment = "Approved by admin",
        createdAt = timestamp,
        resolvedAt = timestamp.plusSeconds(60),
    )

    @Test
    fun `getAllSkipsForMe should return 200 and list of skips`() {
        every { onboardingSkipService.getAllSkipsForMe(authId) } returns listOf(buildGetSkipResponse())

        mockMvc
            .perform(get("/api/v1/onboarding/me/skips").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.getAllSkipsForMe(authId) }
    }

    @Test
    fun `getAllSkipsForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/skips"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getAllSkipsForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/skips").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getSkipsByStepIdForMe should return 200 and list of skips`() {
        every { onboardingSkipService.getSkipsByStepIdForMe(authId, stepId) } returns listOf(buildGetSkipResponse())

        mockMvc
            .perform(get("/api/v1/onboarding/me/steps/$stepId/skips").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.getSkipsByStepIdForMe(authId, stepId) }
    }

    @Test
    fun `getSkipByIdForMe should return 200 and skip`() {
        every { onboardingSkipService.getSkipByIdForMe(authId, skipId) } returns buildGetSkipResponse()

        mockMvc
            .perform(get("/api/v1/onboarding/me/skips/$skipId").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.getSkipByIdForMe(authId, skipId) }
    }

    @Test
    fun `getSkipByIdForMe should return 404 when not found`() {
        every { onboardingSkipService.getSkipByIdForMe(authId, skipId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/onboarding/me/skips/$skipId").with(userJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingSkipService.getSkipByIdForMe(authId, skipId) }
    }

    @Test
    fun `createSkipAtStepForMe should return 201 and created skip`() {
        val request = buildCreateRequest()
        every {
            onboardingSkipService.createOnboardingSkipForMe(authId, stepId, request)
        } returns buildCreateSkipResponse()

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/steps/$stepId/skips")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.createOnboardingSkipForMe(authId, stepId, request) }
    }

    @Test
    fun `updateSkipByIdForMe should return 200 and updated step`() {
        val request = buildUpdateRequest()
        every {
            onboardingSkipService.updateOnboardingSkipForMe(authId, skipId, request)
        } returns buildUpdateStepResponse()

        mockMvc
            .perform(
                put("/api/v1/onboarding/me/skips/$skipId")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.updateOnboardingSkipForMe(authId, skipId, request) }
    }

    @Test
    fun `deleteSkipByIdForMe should return 204`() {
        every { onboardingSkipService.deleteSkipByIdForMe(authId, skipId) } just Runs

        mockMvc
            .perform(delete("/api/v1/onboarding/me/skips/$skipId").with(userJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingSkipService.deleteSkipByIdForMe(authId, skipId) }
    }

    @Test
    fun `deleteSkipByIdForMe should return 400 when skip is not pending`() {
        every { onboardingSkipService.deleteSkipByIdForMe(authId, skipId) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST)

        mockMvc
            .perform(delete("/api/v1/onboarding/me/skips/$skipId").with(userJwt))
            .andExpect(status().isBadRequest)

        verify(exactly = 1) { onboardingSkipService.deleteSkipByIdForMe(authId, skipId) }
    }

    @Test
    fun `getAllSkips should return 200 and list of skips`() {
        every { onboardingSkipService.getAllSkips() } returns listOf(buildGetAllSkipResponse())

        mockMvc
            .perform(get("/api/v1/admin/onboarding/skips").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.getAllSkips() }
    }

    @Test
    fun `getAllSkips should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/admin/onboarding/skips"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getAllSkips should return 403 when authenticated without admin role`() {
        mockMvc
            .perform(get("/api/v1/admin/onboarding/skips").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getAllSkipsByUserId should return 200 and list of skips`() {
        every { onboardingSkipService.getAllSkipsByUserId(userId) } returns listOf(buildGetSkipResponse())

        mockMvc
            .perform(get("/api/v1/admin/onboarding/users/$userId/skips").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.getAllSkipsByUserId(userId) }
    }

    @Test
    fun `getSkipsByStepId should return 200 and list of skips`() {
        every { onboardingSkipService.getAllSkipsByStepId(stepId) } returns listOf(buildGetSkipResponse())

        mockMvc
            .perform(get("/api/v1/admin/onboarding/steps/$stepId/skips").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.getAllSkipsByStepId(stepId) }
    }

    @Test
    fun `getSkipById should return 200 and skip`() {
        every { onboardingSkipService.getSkipById(skipId) } returns buildGetSkipResponse()

        mockMvc
            .perform(get("/api/v1/admin/onboarding/skips/$skipId").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.getSkipById(skipId) }
    }

    @Test
    fun `acceptSkipById should return 200 and review response`() {
        val request = buildReviewRequest()
        every { onboardingSkipService.acceptSkipById(skipId, request) } returns buildReviewResponse(SkipStatus.ACCEPTED)

        mockMvc
            .perform(
                post("/api/v1/admin/onboarding/skips/$skipId/accept")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.acceptSkipById(skipId, request) }
    }

    @Test
    fun `denySkipById should return 200 and review response`() {
        val request = buildReviewRequest()
        every { onboardingSkipService.denySkipById(skipId, request) } returns buildReviewResponse(SkipStatus.DENIED)

        mockMvc
            .perform(
                post("/api/v1/admin/onboarding/skips/$skipId/deny")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingSkipService.denySkipById(skipId, request) }
    }

    @Test
    fun `deleteSkipById should return 204`() {
        every { onboardingSkipService.deleteSkipById(skipId) } just Runs

        mockMvc
            .perform(delete("/api/v1/admin/onboarding/skips/$skipId").with(adminJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingSkipService.deleteSkipById(skipId) }
    }

    @Test
    fun `deleteSkipById should return 400 when skip is not pending`() {
        every { onboardingSkipService.deleteSkipById(skipId) } throws
            ResponseStatusException(HttpStatus.BAD_REQUEST)

        mockMvc
            .perform(delete("/api/v1/admin/onboarding/skips/$skipId").with(adminJwt))
            .andExpect(status().isBadRequest)

        verify(exactly = 1) { onboardingSkipService.deleteSkipById(skipId) }
    }
}
