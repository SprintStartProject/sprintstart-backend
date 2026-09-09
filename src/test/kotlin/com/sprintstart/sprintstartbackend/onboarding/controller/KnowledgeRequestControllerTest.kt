package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.KnowledgeRequestStatus
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.EscalationHireResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.knowledge.KnowledgeRequestResponse
import com.sprintstart.sprintstartbackend.onboarding.service.KnowledgeBaseService
import com.sprintstart.sprintstartbackend.user.security.ProjectAuthorization
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
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

    // Required by the slice: the route's `@PreAuthorize` names this bean, and without it the
    // expression fails to resolve at request time (see `ProjectAuthorization`'s KDoc). Registered
    // under that exact name, because SpEL's `@projectAuth` is a lookup by name, not by type.
    @MockkBean(name = "projectAuth")
    private lateinit var projectAuth: ProjectAuthorization

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

    @BeforeEach
    fun allowTheProject() {
        every { projectAuth.canAccessProject(any(), projectId) } returns true
    }

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

    // The role alone used to be the whole gate, so any PM or HR user could read any project's queue
    // by passing its id. That mattered less while the payload was a question and a bare UUID; it now
    // carries the asker's name and how far through onboarding they are.
    @Test
    fun `a PM on another project cannot read this project's queue`() {
        every { projectAuth.canAccessProject(any(), projectId) } returns false

        val fromOutside = get("/api/v1/onboarding/knowledge-requests")
            .param("projectId", projectId.toString())
            .with(pmJwt)

        mockMvc.perform(fromOutside).andExpect(status().isForbidden)
        verify(exactly = 0) { knowledgeBaseService.listOpen(any()) }
    }

    @Test
    fun `the count is served as a number, without resolving anybody`() {
        every { knowledgeBaseService.countOpen(projectId) } returns 3L

        mockMvc
            .perform(
                get("/api/v1/onboarding/knowledge-requests/count").param("projectId", projectId.toString()).with(pmJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.open").value(3))

        verify(exactly = 0) { knowledgeBaseService.listOpen(any()) }
    }

    @Test
    fun `the count is project-scoped too`() {
        every { projectAuth.canAccessProject(any(), projectId) } returns false

        val fromOutside = get("/api/v1/onboarding/knowledge-requests/count")
            .param("projectId", projectId.toString())
            .with(pmJwt)

        mockMvc.perform(fromOutside).andExpect(status().isForbidden)
    }
}
