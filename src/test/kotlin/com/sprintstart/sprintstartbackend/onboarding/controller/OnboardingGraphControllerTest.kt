package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepStatus
import com.sprintstart.sprintstartbackend.onboarding.external.enums.StepType
import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.ArrangeOnboardingGraphRequest
import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.OnboardingGraphNodePosition
import com.sprintstart.sprintstartbackend.onboarding.model.request.graph.ReplaceOnboardingBlockersRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.graph.OnboardingBlockersResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.step.CreateOnboardingStepResponse
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingGraphService
import com.sprintstart.sprintstartbackend.onboarding.service.OnboardingStepPlacementService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(OnboardingGraphController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class OnboardingGraphControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var onboardingGraphService: OnboardingGraphService

    @MockkBean
    private lateinit var onboardingStepPlacementService: OnboardingStepPlacementService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val authId = "test-auth-id"
    private val phaseId = UUID.randomUUID()
    private val nodeId = UUID.randomUUID()
    private val blockerId = UUID.randomUUID()

    private fun jwtWithRoles(vararg roles: String): JwtRequestPostProcessor =
        jwt()
            .jwt { it.subject(authId) }
            .authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })

    private val userJwt = jwtWithRoles("USER")
    private val pmJwt = jwtWithRoles("USER", "PM")

    @Test
    fun `a hire can arrange a phase of their own path`() {
        val request = ArrangeOnboardingGraphRequest(listOf(OnboardingGraphNodePosition(nodeId, 12.0, 34.0)))
        every { onboardingGraphService.arrangePhaseForMe(authId, phaseId, request) } just Runs

        mockMvc
            .perform(
                put("/api/v1/onboarding/me/phases/$phaseId/graph")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"nodes":[{"id":"$nodeId","graphX":12.0,"graphY":34.0}]}"""),
            ).andExpect(status().isNoContent)

        verify(exactly = 1) { onboardingGraphService.arrangePhaseForMe(authId, phaseId, request) }
    }

    @Test
    fun `a hire cannot change what waits on what`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/nodes/$nodeId/blockers")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"blockerIds":["$blockerId"]}"""),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `a PM can replace the blockers of a node`() {
        every {
            onboardingGraphService.replaceNodeBlockers(nodeId, ReplaceOnboardingBlockersRequest(setOf(blockerId)))
        } returns OnboardingBlockersResponse(nodeId, setOf(blockerId))

        mockMvc
            .perform(
                put("/api/v1/onboarding/nodes/$nodeId/blockers")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"blockerIds":["$blockerId"]}"""),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.blockerIds[0]").value(blockerId.toString()))
    }

    @Test
    fun `a PM can add a connected step`() {
        val stepId = UUID.randomUUID()
        every {
            onboardingStepPlacementService.createConnectedStepForPhase(
                phaseId,
                any(),
                setOf(blockerId),
                emptySet(),
                100.0,
                200.0,
            )
        } returns CreateOnboardingStepResponse(
            id = stepId,
            phaseId = phaseId,
            position = 0,
            title = "Pair with the tech lead",
            description = "",
            type = StepType.TASK,
            estimatedMinutes = 30,
            isAiAssisted = false,
            expectedOutcome = "",
            status = StepStatus.WAITING,
        )

        mockMvc
            .perform(
                post("/api/v1/onboarding/phases/$phaseId/steps/connected")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"step":{"position":0,"title":"Pair with the tech lead","description":"",
                        "type":"TASK","estimatedMinutes":30,"expectedOutcome":""},
                        "waitsOn":["$blockerId"],"unlocks":[],"graphX":100.0,"graphY":200.0}
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(stepId.toString()))
    }
}
