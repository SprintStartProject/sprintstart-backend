package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.service.CompetencyDashboardService
import io.mockk.every
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.data.domain.PageImpl
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest(CompetencyDashboardController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class CompetencyDashboardControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var competencyDashboardService: CompetencyDashboardService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private fun jwtWithRoles(vararg roles: String): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject("test-subject")
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })
    }

    private val pmJwt = jwtWithRoles("PM")
    private val userJwt = jwtWithRoles("USER")

    @Test
    fun `getCompetencyAggregate should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/dashboard/competencies"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getCompetencyAggregate should return 403 for a plain USER`() {
        mockMvc
            .perform(get("/api/v1/onboarding/dashboard/competencies").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getCompetencyAggregate should return 200 for a PM`() {
        every { competencyDashboardService.getCompetencyAggregate() } returns emptyList()

        mockMvc
            .perform(get("/api/v1/onboarding/dashboard/competencies").with(pmJwt))
            .andExpect(status().isOk)
    }

    @Test
    fun `getUserCompetencySummaries should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/dashboard/users"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getUserCompetencySummaries should return 403 for a plain USER`() {
        mockMvc
            .perform(get("/api/v1/onboarding/dashboard/users").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getUserCompetencySummaries should return 200 for a PM`() {
        every {
            competencyDashboardService.getUserCompetencySummaries(any(), any(), any(), any())
        } returns PageImpl(emptyList())

        mockMvc
            .perform(get("/api/v1/onboarding/dashboard/users").with(pmJwt))
            .andExpect(status().isOk)
    }

    @Test
    fun `getUserCompetencyStates should return 403 for a plain USER`() {
        mockMvc
            .perform(get("/api/v1/onboarding/dashboard/users/${UUID.randomUUID()}").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getUserCompetencyStates should return 200 for a PM`() {
        val userId = UUID.randomUUID()
        every { competencyDashboardService.getUserCompetencyStates(userId) } returns emptyList()

        mockMvc
            .perform(get("/api/v1/onboarding/dashboard/users/$userId").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$").isArray)
    }
}
