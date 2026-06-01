package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.CreateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.step.UpdateOnboardingStepRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.GetOnboardingStepsResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.UpdateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.delete
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.Instant
import java.util.UUID

@WebMvcTest(OnboardingStepController::class)
class OnboardingStepControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var onboardingService: OnboardingService

    private val phaseId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val stepId: UUID = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val createdAt: Instant = Instant.parse("2026-06-01T10:00:00Z")

    @Test
    fun `createOnboardingStep should return 201 and created step`() {
        val request = CreateOnboardingStepRequest(
            position = 1,
            title = "Read docs",
            description = "Read the internal docs",
            type = StepType.DOCUMENT,
            estimatedMinutes = 30,
            expectedOutcome = "Understands the basics",
        )
        val response = CreateOnboardingStepResponse(
            id = stepId,
            phaseId = phaseId,
            position = 1,
            title = "Read docs",
            description = "Read the internal docs",
            estimatedMinutes = 30,
            expectedOutcome = "Understands the basics",
            status = StepStatus.WAITING,
        )

        every { onboardingService.createOnboardingStepForPhaseId(phaseId, request) } returns response

        mockMvc
            .post("/api/v1/onboarding/phases/$phaseId/steps") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { value(stepId.toString()) }
                jsonPath("$.phaseId") { value(phaseId.toString()) }
                jsonPath("$.status") { value("WAITING") }
            }

        verify(exactly = 1) { onboardingService.createOnboardingStepForPhaseId(phaseId, request) }
    }

    @Test
    fun `getOnboardingSteps should return 200 and all steps`() {
        val response = listOf(
            GetOnboardingStepsResponse(
                id = stepId,
                phaseId = phaseId,
                position = 1,
                title = "Read docs",
                description = "Read the internal docs",
                estimatedMinutes = 30,
                status = StepStatus.WAITING,
                completedAt = null,
                skipReason = null,
            ),
        )

        every { onboardingService.getOnboardingSteps() } returns response

        mockMvc
            .get("/api/v1/onboarding/steps")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(stepId.toString()) }
                jsonPath("$[0].phaseId") { value(phaseId.toString()) }
                jsonPath("$[0].status") { value("WAITING") }
            }

        verify(exactly = 1) { onboardingService.getOnboardingSteps() }
    }

    @Test
    fun `getOnboardingStepsForPhaseId should return 200 and steps for phase`() {
        val response = listOf(
            GetOnboardingStepResponse(
                id = stepId,
                phaseId = phaseId,
                position = 1,
                title = "Read docs",
                description = "Read the internal docs",
                estimatedMinutes = 30,
                tasks = emptyList(),
                resources = emptyList(),
                status = StepStatus.WAITING,
                completedAt = null,
                skipReason = null,
            ),
        )

        every { onboardingService.getOnboardingStepsByPhaseId(phaseId) } returns response

        mockMvc
            .get("/api/v1/onboarding/phases/$phaseId/steps")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(stepId.toString()) }
                jsonPath("$[0].tasks.length()") { value(0) }
                jsonPath("$[0].resources.length()") { value(0) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingStepsByPhaseId(phaseId) }
    }

    @Test
    fun `getOnboardingStep should return 200 and step`() {
        val response = GetOnboardingStepResponse(
            id = stepId,
            phaseId = phaseId,
            position = 1,
            title = "Read docs",
            description = "Read the internal docs",
            estimatedMinutes = 30,
            tasks = emptyList(),
            resources = emptyList(),
            status = StepStatus.WAITING,
            completedAt = null,
            skipReason = null,
        )

        every { onboardingService.getOnboardingStep(stepId) } returns response

        mockMvc
            .get("/api/v1/onboarding/steps/$stepId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(stepId.toString()) }
                jsonPath("$.phaseId") { value(phaseId.toString()) }
                jsonPath("$.status") { value("WAITING") }
            }

        verify(exactly = 1) { onboardingService.getOnboardingStep(stepId) }
    }

    @Test
    fun `updateOnboardingStep should return 200 and updated step`() {
        val request = UpdateOnboardingStepRequest(
            position = 2,
            title = "Updated docs",
            description = "Updated docs description",
            type = StepType.DOCUMENT,
            estimatedMinutes = 45,
            expectedOutcome = "Understands updated docs",
            status = StepStatus.FINISHED,
            skipReason = null,
        )
        val response = UpdateOnboardingStepResponse(
            id = stepId,
            phaseId = phaseId,
            position = 2,
            title = "Updated docs",
            description = "Updated docs description",
            estimatedMinutes = 45,
            expectedOutcome = "Understands updated docs",
            status = StepStatus.FINISHED,
            completedAt = createdAt,
            skipReason = null,
        )

        every { onboardingService.updateOnboardingStep(stepId, request) } returns response

        mockMvc
            .put("/api/v1/onboarding/steps/$stepId") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(stepId.toString()) }
                jsonPath("$.position") { value(2) }
                jsonPath("$.status") { value("FINISHED") }
            }

        verify(exactly = 1) { onboardingService.updateOnboardingStep(stepId, request) }
    }

    @Test
    fun `deleteOnboardingStep should return 204`() {
        every { onboardingService.deleteOnboardingStep(stepId) } just Runs

        mockMvc
            .delete("/api/v1/onboarding/steps/$stepId")
            .andExpect {
                status { isNoContent() }
            }

        verify(exactly = 1) { onboardingService.deleteOnboardingStep(stepId) }
    }
}
