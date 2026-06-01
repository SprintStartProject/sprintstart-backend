package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.CreateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.phase.UpdateOnboardingPhaseRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.CreateOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhaseResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.GetOnboardingPhasesResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.phase.UpdateOnboardingPhaseResponse
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
import java.util.UUID

@WebMvcTest(OnboardingPhaseController::class)
class OnboardingPhaseControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    private val objectMapper = jacksonObjectMapper()

    @MockkBean
    private lateinit var onboardingService: OnboardingService

    private val pathId: UUID = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val phaseId: UUID = UUID.fromString("33333333-3333-3333-3333-333333333333")

    @Test
    fun `createOnboardingPhase should return 201 and created phase`() {
        val request = CreateOnboardingPhaseRequest(
            position = 1,
            title = "Setup",
            description = "Setup phase",
        )
        val response = CreateOnboardingPhaseResponse(
            id = phaseId,
            pathId = pathId,
            position = 1,
            title = "Setup",
            description = "Setup phase",
        )

        every { onboardingService.createOnboardingPhaseForPathId(pathId, request) } returns response

        mockMvc
            .post("/api/v1/onboarding/paths/$pathId/phases") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isCreated() }
                jsonPath("$.id") { value(phaseId.toString()) }
                jsonPath("$.pathId") { value(pathId.toString()) }
                jsonPath("$.position") { value(1) }
                jsonPath("$.title") { value("Setup") }
                jsonPath("$.description") { value("Setup phase") }
            }

        verify(exactly = 1) { onboardingService.createOnboardingPhaseForPathId(pathId, request) }
    }

    @Test
    fun `getOnboardingPhases should return 200 and all phases`() {
        val response = listOf(
            GetOnboardingPhasesResponse(
                id = phaseId,
                pathId = pathId,
                position = 1,
                title = "Setup",
                description = "Setup phase",
            ),
        )

        every { onboardingService.getOnboardingPhases() } returns response

        mockMvc
            .get("/api/v1/onboarding/phases")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(phaseId.toString()) }
                jsonPath("$[0].pathId") { value(pathId.toString()) }
                jsonPath("$[0].position") { value(1) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingPhases() }
    }

    @Test
    fun `getAllOnboardingPhasesByPathId should return 200 and phases for path`() {
        val response = listOf(
            GetOnboardingPhaseResponse(
                id = phaseId,
                pathId = pathId,
                position = 1,
                title = "Setup",
                description = "Setup phase",
                steps = emptyList(),
            ),
        )

        every { onboardingService.getOnboardingPhasesByPathId(pathId) } returns response

        mockMvc
            .get("/api/v1/onboarding/paths/$pathId/phases")
            .andExpect {
                status { isOk() }
                jsonPath("$[0].id") { value(phaseId.toString()) }
                jsonPath("$[0].steps.length()") { value(0) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingPhasesByPathId(pathId) }
    }

    @Test
    fun `getOnboardingPhase should return 200 and phase`() {
        val response = GetOnboardingPhaseResponse(
            id = phaseId,
            pathId = pathId,
            position = 1,
            title = "Setup",
            description = "Setup phase",
            steps = emptyList(),
        )

        every { onboardingService.getOnboardingPhase(phaseId) } returns response

        mockMvc
            .get("/api/v1/onboarding/phases/$phaseId")
            .andExpect {
                status { isOk() }
                jsonPath("$.id") { value(phaseId.toString()) }
                jsonPath("$.pathId") { value(pathId.toString()) }
            }

        verify(exactly = 1) { onboardingService.getOnboardingPhase(phaseId) }
    }

    @Test
    fun `updateOnboardingPhase should return 200 and updated phase`() {
        val request = UpdateOnboardingPhaseRequest(
            position = 2,
            title = "Updated setup",
            description = "Updated setup phase",
        )
        val response = UpdateOnboardingPhaseResponse(
            id = phaseId,
            pathId = pathId,
            position = 2,
            title = "Updated setup",
            description = "Updated setup phase",
        )

        every { onboardingService.updateOnboardingPhase(phaseId, request) } returns response

        mockMvc
            .put("/api/v1/onboarding/phases/$phaseId") {
                contentType = MediaType.APPLICATION_JSON
                content = objectMapper.writeValueAsString(request)
            }.andExpect {
                status { isOk() }
                jsonPath("$.id") { value(phaseId.toString()) }
                jsonPath("$.position") { value(2) }
                jsonPath("$.title") { value("Updated setup") }
            }

        verify(exactly = 1) { onboardingService.updateOnboardingPhase(phaseId, request) }
    }

    @Test
    fun `deleteOnboardingPhase should return 204`() {
        every { onboardingService.deleteOnboardingPhase(phaseId) } just Runs

        mockMvc
            .delete("/api/v1/onboarding/phases/$phaseId")
            .andExpect {
                status { isNoContent() }
            }

        verify(exactly = 1) { onboardingService.deleteOnboardingPhase(phaseId) }
    }
}
