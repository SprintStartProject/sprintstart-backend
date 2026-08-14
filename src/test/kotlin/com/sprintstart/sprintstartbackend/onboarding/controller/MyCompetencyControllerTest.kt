package com.sprintstart.sprintstartbackend.onboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencySource
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.MyCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.service.MyCompetencyService
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
import java.time.Instant

@WebMvcTest(MyCompetencyController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class MyCompetencyControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var myCompetencyService: MyCompetencyService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val authId = "test-auth-id"

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

    @Test
    fun `getMyCompetencies should return 200 and the ledger`() {
        every { myCompetencyService.getMyCompetencies(authId) } returns listOf(
            MyCompetencyResponse(
                competencyKey = "kotlin",
                label = "Kotlin",
                kind = CompetencyKind.SKILL,
                level = 3,
                targetLevel = 2,
                source = CompetencySource.VERIFIED,
                updatedAt = Instant.parse("2026-07-19T00:00:00Z"),
            ),
        )

        mockMvc
            .perform(get("/api/v1/onboarding/me/competencies").with(userJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].competencyKey").value("kotlin"))
            .andExpect(jsonPath("$[0].kind").value("SKILL"))
            // The bar travels with the row so a client can tell "holds this" from "progressing".
            .andExpect(jsonPath("$[0].targetLevel").value(2))
            .andExpect(jsonPath("$[0].source").value("VERIFIED"))

        verify(exactly = 1) { myCompetencyService.getMyCompetencies(authId) }
    }

    @Test
    fun `getMyCompetencies should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/competencies"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `getMyCompetencies should return 403 when authenticated with wrong role`() {
        mockMvc
            .perform(get("/api/v1/onboarding/me/competencies").with(noUserRoleJwt))
            .andExpect(status().isForbidden)
    }
}
