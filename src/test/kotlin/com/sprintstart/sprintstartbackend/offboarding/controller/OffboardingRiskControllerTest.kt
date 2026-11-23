package com.sprintstart.sprintstartbackend.offboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileRisk
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskLevel
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskMap
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskSummary
import com.sprintstart.sprintstartbackend.offboarding.scoring.ScoringProfile
import com.sprintstart.sprintstartbackend.offboarding.service.OffboardingRiskService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [OffboardingRiskController::class])
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class OffboardingRiskControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var service: OffboardingRiskService

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuthorization: ProjectAuthorization

    private val projectId = UUID.randomUUID()
    private val asOf = Instant.parse("2026-11-20T12:00:00Z")
    private val pmJwt = jwt().jwt { it.subject("pm-id") }.authorities(SimpleGrantedAuthority("ROLE_PM"))
    private val userJwt = jwt().jwt { it.subject("user-id") }.authorities(SimpleGrantedAuthority("ROLE_USER"))

    private fun risk(path: String, level: RiskLevel) = FileRisk(
        repository = "github:acme/app",
        path = path,
        level = level,
        score = 0.81234,
        departingShare = 1.0,
        recentActivity = 0.4,
        fragmentation = 0.0,
        remainingKnowers = if (level == RiskLevel.HOARDED) listOf(PersonKey("ben")) else emptyList(),
        lastChangedAt = asOf,
    )

    private fun riskMap(vararg files: FileRisk) = RiskMap(
        asOf = asOf,
        profile = ScoringProfile(),
        departing = setOf(PersonKey("ada")),
        unknownDeparting = emptySet(),
        summary = RiskSummary(files = 10, orphaned = 2, hoarded = 1, alreadyOrphaned = 3),
        files = files.toList(),
    )

    private fun allowManager() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns true
    }

    @Test
    fun `a project manager gets the ranked files with the measures behind them`() {
        allowManager()
        every { service.riskMap(ProjectId(projectId), any()) } returns
            riskMap(risk("core.kt", RiskLevel.ORPHANED), risk("util.kt", RiskLevel.HOARDED))

        mockMvc
            .perform(get("/api/v1/projects/$projectId/offboarding/risk").param("leaving", "ada").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.departing[0]").value("ada"))
            .andExpect(jsonPath("$.settings.halfLifeDays").value(152.0))
            .andExpect(jsonPath("$.summary.files").value(10))
            .andExpect(jsonPath("$.summary.orphaned").value(2))
            .andExpect(jsonPath("$.summary.listed").value(2))
            .andExpect(jsonPath("$.files[0].path").value("core.kt"))
            .andExpect(jsonPath("$.files[0].level").value("ORPHANED"))
            .andExpect(jsonPath("$.files[0].score").value(0.812))
            .andExpect(jsonPath("$.files[1].remainingKnowers[0]").value("ben"))
    }

    @Test
    fun `the query is handed to the service as a typed request`() {
        allowManager()
        val request = slot<RiskRequest>()
        every { service.riskMap(ProjectId(projectId), capture(request)) } returns riskMap()

        mockMvc
            .perform(
                get("/api/v1/projects/$projectId/offboarding/risk")
                    .param("leaving", "Ada@X.org", "ben")
                    .param("asOf", "2026-05-01T00:00:00Z")
                    .param("halfLifeDays", "30")
                    .param("inactiveAfterDays", "90")
                    .param("authorThreshold", "0.6")
                    .with(pmJwt),
            ).andExpect(status().isOk)

        assertThat(request.captured.departing).containsExactlyInAnyOrder(PersonKey("ada@x.org"), PersonKey("ben"))
        assertThat(request.captured.asOf).isEqualTo(Instant.parse("2026-05-01T00:00:00Z"))
        assertThat(request.captured.profile)
            .isEqualTo(ScoringProfile(halfLifeDays = 30.0, inactiveAfterDays = 90, authorThreshold = 0.6))
    }

    @Test
    fun `the limit shortens the list but not the summary`() {
        allowManager()
        every { service.riskMap(ProjectId(projectId), any()) } returns
            riskMap(risk("a.kt", RiskLevel.ORPHANED), risk("b.kt", RiskLevel.ORPHANED), risk("c.kt", RiskLevel.HOARDED))

        mockMvc
            .perform(
                get(
                    "/api/v1/projects/$projectId/offboarding/risk",
                ).param("leaving", "ada").param("limit", "2").with(pmJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.files.length()").value(2))
            .andExpect(jsonPath("$.summary.listed").value(2))
            .andExpect(jsonPath("$.summary.files").value(10))
    }

    @Test
    fun `naming nobody is a bad request and runs nothing`() {
        allowManager()

        mockMvc
            .perform(get("/api/v1/projects/$projectId/offboarding/risk").with(pmJwt))
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { service.riskMap(any(), any()) }
    }

    @Test
    fun `a setting out of range is a bad request`() {
        allowManager()

        mockMvc
            .perform(
                get(
                    "/api/v1/projects/$projectId/offboarding/risk",
                ).param("leaving", "ada").param("halfLifeDays", "0").with(pmJwt),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `someone who does not manage the project is refused`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns false

        mockMvc
            .perform(get("/api/v1/projects/$projectId/offboarding/risk").param("leaving", "ada").with(pmJwt))
            .andExpect(status().isForbidden)

        verify(exactly = 0) { service.riskMap(any(), any()) }
    }

    @Test
    fun `a user without the manager role is refused`() {
        allowManager()

        mockMvc
            .perform(get("/api/v1/projects/$projectId/offboarding/risk").param("leaving", "ada").with(userJwt))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `an anonymous request is refused`() {
        mockMvc
            .perform(get("/api/v1/projects/$projectId/offboarding/risk").param("leaving", "ada"))
            .andExpect(status().isUnauthorized)
    }
}
