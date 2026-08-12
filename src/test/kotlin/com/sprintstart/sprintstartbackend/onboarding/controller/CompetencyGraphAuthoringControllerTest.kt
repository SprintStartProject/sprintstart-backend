package com.sprintstart.sprintstartbackend.onboarding.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.CompetencyGraphResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.CompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.competency.DeleteCompetencyResponse
import com.sprintstart.sprintstartbackend.onboarding.service.CompetencyGraphAuthoringService
import io.mockk.every
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(CompetencyGraphAuthoringController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class CompetencyGraphAuthoringControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var competencyGraphAuthoringService: CompetencyGraphAuthoringService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    private val objectMapper = jacksonObjectMapper()

    private fun jwtWithRoles(vararg roles: String): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject("test-subject")
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { SimpleGrantedAuthority("ROLE_$it") })
    }

    private val pmJwt = jwtWithRoles("PM")
    private val hrJwt = jwtWithRoles("HR")
    private val userJwt = jwtWithRoles("USER")

    private fun liveCompetencyResponse(): CompetencyResponse =
        CompetencyResponse(
            key = "kotlin",
            label = "Kotlin Basics",
            description = null,
            kind = CompetencyKind.SKILL,
            area = "Authentication",
            targetLevel = 3,
        )

    @Test
    fun `getGraph should return the whole graph for a PM`() {
        every { competencyGraphAuthoringService.getGraph() } returns CompetencyGraphResponse(
            competencies = listOf(liveCompetencyResponse()),
        )

        mockMvc
            .perform(get("/api/v1/onboarding/competency-graph").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.competencies[0].key").value("kotlin"))
    }

    @Test
    fun `getGraph should be readable by HR, which reviews the graph without authoring it`() {
        every { competencyGraphAuthoringService.getGraph() } returns CompetencyGraphResponse(
            competencies = emptyList(),
        )

        mockMvc
            .perform(get("/api/v1/onboarding/competency-graph").with(hrJwt))
            .andExpect(status().isOk)
    }

    @Test
    fun `getGraph should return 403 for a plain USER`() {
        mockMvc
            .perform(get("/api/v1/onboarding/competency-graph").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `createCompetency should return 200 and the created node for a PM`() {
        every { competencyGraphAuthoringService.createCompetency(any()) } returns liveCompetencyResponse()

        mockMvc
            .perform(
                post("/api/v1/onboarding/competency-graph/competencies")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf("key" to "kotlin", "label" to "Kotlin Basics", "kind" to "SKILL"),
                        ),
                    ),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value("kotlin"))
    }

    @Test
    fun `createCompetency should return 403 for HR, which reviews but does not author`() {
        mockMvc
            .perform(
                post("/api/v1/onboarding/competency-graph/competencies")
                    .with(hrJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            mapOf("key" to "kotlin", "label" to "Kotlin Basics", "kind" to "SKILL"),
                        ),
                    ),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `updateCompetency should return 200 for a PM`() {
        every { competencyGraphAuthoringService.updateCompetency("kotlin", any()) } returns liveCompetencyResponse()

        mockMvc
            .perform(
                put("/api/v1/onboarding/competency-graph/competencies/kotlin")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("label" to "Kotlin Basics", "targetLevel" to 3))),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value("kotlin"))
    }

    @Test
    fun `updateCompetency should return 403 for a plain USER`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/competency-graph/competencies/kotlin")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("label" to "Kotlin Basics"))),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `updateCompetency should return 403 for HR, which reviews but does not author`() {
        mockMvc
            .perform(
                put("/api/v1/onboarding/competency-graph/competencies/kotlin")
                    .with(jwtWithRoles("HR"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(mapOf("label" to "Kotlin Basics"))),
            ).andExpect(status().isForbidden)
    }

    @Test
    fun `deleteCompetency should return 200 for a PM`() {
        every { competencyGraphAuthoringService.deleteCompetency("kotlin") } returns
            DeleteCompetencyResponse(key = "kotlin")

        mockMvc
            .perform(delete("/api/v1/onboarding/competency-graph/competencies/kotlin").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.key").value("kotlin"))
    }

    @Test
    fun `deleteCompetency should return 403 for a plain USER`() {
        mockMvc
            .perform(delete("/api/v1/onboarding/competency-graph/competencies/kotlin").with(userJwt))
            .andExpect(status().isForbidden)
    }
}
