package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.EscalationHireResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.KnowledgeRequestResponse
import com.sprintstart.sprintstartbackend.onboarding.service.KnowledgeBaseService
import io.mockk.every
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
import java.time.Instant
import java.util.UUID

@WebMvcTest(KnowledgeRequestController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class KnowledgeRequestControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var knowledgeBaseService: KnowledgeBaseService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val authId = "test-auth-id"
    private val hireId = UUID.randomUUID()
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

    private val pmJwt = jwtWithSubject(authId, "PM")
    private val userJwt = jwtWithSubject(authId, "USER")

    private fun request(hire: EscalationHireResponse?) =
        KnowledgeRequestResponse(
            id = UUID.randomUUID(),
            projectId = projectId,
            hireId = hireId,
            question = "How do we deploy?",
            status = KnowledgeRequestStatus.OPEN,
            createdAt = Instant.parse("2026-09-01T09:00:00Z"),
            answeredAt = null,
            answer = null,
            hire = hire,
        )

    @Test
    fun `the inbox serves who asked and where they are`() {
        every { knowledgeBaseService.listOpen(projectId) } returns
            listOf(
                request(
                    EscalationHireResponse(
                        userId = hireId,
                        displayName = "Sam Hire",
                        profileIcon = "fox",
                        currentPhase = "Getting started",
                        currentStep = "Set up your machine",
                        progressPercentage = 0.25,
                    ),
                ),
            )

        mockMvc
            .perform(get("/api/v1/onboarding/knowledge-requests").param("projectId", projectId.toString()).with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].hire.userId").value(hireId.toString()))
            .andExpect(jsonPath("$[0].hire.displayName").value("Sam Hire"))
            .andExpect(jsonPath("$[0].hire.profileIcon").value("fox"))
            .andExpect(jsonPath("$[0].hire.currentPhase").value("Getting started"))
            .andExpect(jsonPath("$[0].hire.currentStep").value("Set up your machine"))
            .andExpect(jsonPath("$[0].hire.progressPercentage").value(0.25))
    }

    @Test
    fun `a question whose asker could not be resolved is still served, without a hire`() {
        every { knowledgeBaseService.listOpen(projectId) } returns listOf(request(hire = null))

        mockMvc
            .perform(get("/api/v1/onboarding/knowledge-requests").param("projectId", projectId.toString()).with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].hireId").value(hireId.toString()))
            .andExpect(jsonPath("$[0].hire").doesNotExist())
    }

    @Test
    fun `a hire's own escalations carry no identity block`() {
        every { knowledgeBaseService.listMine(authId) } returns listOf(request(hire = null))

        mockMvc
            .perform(get("/api/v1/onboarding/me/knowledge-requests").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].hire").doesNotExist())
    }

    @Test
    fun `the inbox stays closed to a plain member`() {
        val asPlainMember = get("/api/v1/onboarding/knowledge-requests")
            .param("projectId", projectId.toString())
            .with(userJwt)

        mockMvc.perform(asPlainMember).andExpect(status().isForbidden)
    }
}
