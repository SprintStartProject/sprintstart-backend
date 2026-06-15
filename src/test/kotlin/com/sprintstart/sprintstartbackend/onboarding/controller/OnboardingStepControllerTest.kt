package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingStepService
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
import java.util.UUID

@WebMvcTest(OnboardingStepController::class)
@Import(
    SecurityConfig::class,
)
@AutoConfigureMockMvc
class OnboardingStepControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var onboardingStepService: OnboardingStepService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val phaseId = UUID.randomUUID()
    private val stepId = UUID.randomUUID()

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

    private fun buildCreateRequest() = CreateOnboardingStepRequest(
        position = 1,
        title = "Step 1",
        description = "step desc",
        type = StepType.DOCUMENT,
        estimatedMinutes = 30,
        expectedOutcome = "You will understand X",
    )

    private fun buildUpdateRequest() = UpdateOnboardingStepRequest(
        position = 1,
        title = "Updated Step",
        description = "updated desc",
        type = StepType.VIDEO,
        estimatedMinutes = 20,
        expectedOutcome = "You will know Y",
        status = StepStatus.FINISHED,
        skipReason = null,
    )

    private fun buildGetStepResponse() = GetOnboardingStepResponse(
        id = stepId,
        phaseId = phaseId,
        position = 1,
        title = "Step 1",
        description = "step desc",
        type = StepType.DOCUMENT,
        estimatedMinutes = 30,
        tasks = emptyList(),
        resources = emptyList(),
        status = StepStatus.WAITING,
        completedAt = null,
        skipReason = null,
    )

    private fun buildGetStepsResponse() = GetOnboardingStepsResponse(
        id = stepId,
        phaseId = phaseId,
        position = 1,
        title = "Step 1",
        description = "step desc",
        type = StepType.DOCUMENT,
        estimatedMinutes = 30,
        status = StepStatus.WAITING,
        completedAt = null,
        skipReason = null,
    )

    private fun buildCreateStepResponse() = CreateOnboardingStepResponse(
        id = stepId,
        phaseId = phaseId,
        position = 1,
        title = "Step 1",
        description = "step desc",
        type = StepType.DOCUMENT,
        estimatedMinutes = 30,
        expectedOutcome = "You will understand X",
        status = StepStatus.WAITING,
    )

    private fun buildUpdateStepResponse() = UpdateOnboardingStepResponse(
        id = stepId,
        phaseId = phaseId,
        position = 1,
        title = "Updated Step",
        description = "updated desc",
        estimatedMinutes = 20,
        expectedOutcome = "You will know Y",
        status = StepStatus.FINISHED,
        completedAt = null,
        skipReason = null,
    )

    // ========================== /me endpoints ==========================

    @Test
    fun `getOnboardingStepsForMe should return 200 and list of steps`() {
        every { onboardingStepService.getOnboardingStepsForMe(authId, phaseId) } returns listOf(buildGetStepsResponse())

        mockMvc
            .perform(get("/api/v1/onboarding/me/phases/$phaseId/steps").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingStepService.getOnboardingStepsForMe(authId, phaseId) }
    }

    @Test
    fun `getOnboardingStepsForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/phases/$phaseId/steps"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingStepsForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/phases/$phaseId/steps").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createOnboardingStepForMe should return 201 and created step`() {
        val request = buildCreateRequest()
        every { onboardingStepService.createOnboardingStepForMe(authId, phaseId, request) } returns
            buildCreateStepResponse()

        mockMvc
            .perform(
                post("/api/v1/onboarding/me/phases/$phaseId/steps")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingStepService.createOnboardingStepForMe(authId, phaseId, request) }
    }

    @Test
    fun `createOnboardingStepForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/phases/$phaseId/steps")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `createOnboardingStepForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/me/phases/$phaseId/steps")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingStepForMe should return 200 and step`() {
        every { onboardingStepService.getOnboardingStepForMe(authId, stepId) } returns buildGetStepResponse()

        mockMvc
            .perform(get("/api/v1/onboarding/me/step/$stepId").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingStepService.getOnboardingStepForMe(authId, stepId) }
    }

    @Test
    fun `getOnboardingStepForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/step/$stepId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingStepForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/step/$stepId").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingStepForMe should return 404 when not found`() {
        every { onboardingStepService.getOnboardingStepForMe(authId, stepId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/onboarding/me/step/$stepId").with(userJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingStepService.getOnboardingStepForMe(authId, stepId) }
    }

    @Test
    fun `updateOnboardingStepForMe should return 200 and updated step`() {
        val request = buildUpdateRequest()
        every { onboardingStepService.updateOnboardingStepForMe(authId, stepId, request) } returns
            buildUpdateStepResponse()

        mockMvc
            .perform(
                put("/api/v1/onboarding/me/step/$stepId")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingStepService.updateOnboardingStepForMe(authId, stepId, request) }
    }

    @Test
    fun `updateOnboardingStepForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/me/step/$stepId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateOnboardingStepForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/me/step/$stepId")
                    .with(noUserRoleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingStepForMe should return 204`() {
        every { onboardingStepService.deleteOnboardingStepForMe(authId, stepId) } just Runs

        mockMvc
            .perform(delete("/api/v1/onboarding/me/step/$stepId").with(userJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingStepService.deleteOnboardingStepForMe(authId, stepId) }
    }

    @Test
    fun `deleteOnboardingStepForMe should return 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/me/step/$stepId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteOnboardingStepForMe should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/me/step/$stepId").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }

    // ========================== Admin endpoints ==========================

    @Test
    fun `getOnboardingStepsForPhaseId should return 200 and list of steps`() {
        every { onboardingStepService.getOnboardingStepsByPhaseId(phaseId) } returns listOf(buildGetStepResponse())

        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId/steps").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingStepService.getOnboardingStepsByPhaseId(phaseId) }
    }

    @Test
    fun `getOnboardingStepsForPhaseId should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId/steps"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingStepsForPhaseId should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/phases/$phaseId/steps").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createOnboardingStep should return 201 and created step`() {
        val request = buildCreateRequest()
        every { onboardingStepService.createOnboardingStepForPhaseId(phaseId, request) } returns
            buildCreateStepResponse()

        mockMvc
            .perform(
                post("/api/v1/onboarding/phases/$phaseId/steps")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isCreated)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingStepService.createOnboardingStepForPhaseId(phaseId, request) }
    }

    @Test
    fun `createOnboardingStep should return 401 when not authenticated`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/phases/$phaseId/steps")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `createOnboardingStep should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/phases/$phaseId/steps")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildCreateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingStep should return 200 and step`() {
        every { onboardingStepService.getOnboardingStepById(stepId) } returns buildGetStepResponse()

        mockMvc
            .perform(get("/api/v1/onboarding/steps/$stepId").with(adminJwt))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingStepService.getOnboardingStepById(stepId) }
    }

    @Test
    fun `getOnboardingStep should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/steps/$stepId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getOnboardingStep should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/steps/$stepId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getOnboardingStep should return 404 when not found`() {
        every { onboardingStepService.getOnboardingStepById(stepId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(get("/api/v1/onboarding/steps/$stepId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingStepService.getOnboardingStepById(stepId) }
    }

    @Test
    fun `updateOnboardingStep should return 200 and updated step`() {
        val request = buildUpdateRequest()
        every { onboardingStepService.updateOnboardingStepById(stepId, request) } returns buildUpdateStepResponse()

        mockMvc
            .perform(
                put("/api/v1/onboarding/steps/$stepId")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))

        verify(exactly = 1) { onboardingStepService.updateOnboardingStepById(stepId, request) }
    }

    @Test
    fun `updateOnboardingStep should return 401 when not authenticated`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/steps/$stepId")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `updateOnboardingStep should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/steps/$stepId")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(buildUpdateRequest())),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `updateOnboardingStep should return 404 when not found`() {
        val request = buildUpdateRequest()
        every { onboardingStepService.updateOnboardingStepById(stepId, request) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(
                put("/api/v1/onboarding/steps/$stepId")
                    .with(adminJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)),
            ).andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingStepService.updateOnboardingStepById(stepId, request) }
    }

    @Test
    fun `deleteOnboardingStepById should return 204`() {
        every { onboardingStepService.deleteOnboardingStepById(stepId) } just Runs

        mockMvc
            .perform(delete("/api/v1/onboarding/steps/$stepId").with(adminJwt))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingStepService.deleteOnboardingStepById(stepId) }
    }

    @Test
    fun `deleteOnboardingStepById should return 401 when not authenticated`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/steps/$stepId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `deleteOnboardingStepById should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/steps/$stepId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `deleteOnboardingStepById should return 404 when not found`() {
        every { onboardingStepService.deleteOnboardingStepById(stepId) } throws
            ResponseStatusException(HttpStatus.NOT_FOUND)

        mockMvc
            .perform(delete("/api/v1/onboarding/steps/$stepId").with(adminJwt))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { onboardingStepService.deleteOnboardingStepById(stepId) }
    }
}
