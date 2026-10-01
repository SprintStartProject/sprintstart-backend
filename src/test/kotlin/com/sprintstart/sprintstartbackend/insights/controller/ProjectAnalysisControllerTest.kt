package com.sprintstart.sprintstartbackend.insights.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.insights.model.dto.response.ProjectAnalysisRunResponse
import com.sprintstart.sprintstartbackend.insights.service.ProjectAnalysisRunService
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(ProjectAnalysisController::class)
@Import(SecurityConfig::class)
@AutoConfigureMockMvc
class ProjectAnalysisControllerTest(
    @Autowired private val mockMvc: MockMvc,
) {
    @MockkBean
    private lateinit var projectAnalysisRunService: ProjectAnalysisRunService

    @MockkBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuth: ProjectAuthorization

    private val projectId: UUID = UUID.randomUUID()

    @BeforeEach
    fun grantProjectAccess() {
        every { projectAuth.canAccessProject(any(), projectId) } returns true
    }

    private fun jwtWithRoles(vararg roles: String): JwtRequestPostProcessor {
        return jwt()
            .jwt { jwt ->
                jwt.subject("test-auth-id")
                jwt.claim("realm_access", mapOf("roles" to roles.toList()))
            }.authorities(roles.map { role -> SimpleGrantedAuthority("ROLE_$role") })
    }

    private val pmJwt = jwtWithRoles("PM")
    private val userJwt = jwtWithRoles("USER")

    private val run = ProjectAnalysisRunResponse(
        id = UUID.randomUUID(),
        at = Instant.parse("2026-10-01T10:00:00Z"),
        score = 72,
        counts = mapOf("critical" to 1, "warning" to 0, "info" to 0, "good" to 0),
        failedChecks = 0,
        findings = emptyList(),
        tasks = emptyList(),
    )

    private val validBody = """
        {
          "score": 72,
          "findings": [
            {"id": "skips", "severity": "critical", "area": "team", "title": "2 skip requests", "detail": "Anna"}
          ],
          "tasks": [{"id": "team", "label": "Team", "status": "done"}]
        }
    """.trimIndent()

    @Test
    fun `getRuns should return 200 and the runs for a PM`() {
        every { projectAnalysisRunService.list(projectId, 10) } returns listOf(run)

        mockMvc
            .perform(get("/api/v1/insights/project-analysis/runs?projectId=$projectId").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].score").value(72))
            .andExpect(jsonPath("$[0].counts.critical").value(1))
    }

    @Test
    fun `getRuns should return 403 for a non-PM role`() {
        mockMvc
            .perform(get("/api/v1/insights/project-analysis/runs?projectId=$projectId").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getRuns should return 403 for a PM without access to the project`() {
        every { projectAuth.canAccessProject(any(), projectId) } returns false

        mockMvc
            .perform(get("/api/v1/insights/project-analysis/runs?projectId=$projectId").with(pmJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getRuns should return 401 when not authenticated`() {
        mockMvc
            .perform(get("/api/v1/insights/project-analysis/runs?projectId=$projectId"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `saveRun should return 201 and the stored run`() {
        every { projectAnalysisRunService.save(projectId, any()) } returns run

        mockMvc
            .perform(
                post("/api/v1/insights/project-analysis/runs?projectId=$projectId")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.score").value(72))

        verify(exactly = 1) { projectAnalysisRunService.save(projectId, any()) }
    }

    @Test
    fun `saveRun should return 400 for a score above 100`() {
        mockMvc
            .perform(
                post("/api/v1/insights/project-analysis/runs?projectId=$projectId")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody.replace("\"score\": 72", "\"score\": 140")),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `saveRun should return 400 for an unknown severity`() {
        mockMvc
            .perform(
                post("/api/v1/insights/project-analysis/runs?projectId=$projectId")
                    .with(pmJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody.replace("\"critical\"", "\"catastrophic\"")),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `saveRun should return 403 for a non-PM role`() {
        mockMvc
            .perform(
                post("/api/v1/insights/project-analysis/runs?projectId=$projectId")
                    .with(userJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(validBody),
            ).andExpect(status().isForbidden)
    }
}
