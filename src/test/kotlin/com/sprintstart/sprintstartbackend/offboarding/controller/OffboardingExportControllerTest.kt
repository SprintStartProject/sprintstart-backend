package com.sprintstart.sprintstartbackend.offboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.offboarding.model.response.ExportedSnapshotResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskMapResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSettingsResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSummaryResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.StudyExportResponse
import com.sprintstart.sprintstartbackend.offboarding.service.StudyExportService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import io.mockk.every
import io.mockk.verify
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

@WebMvcTest(controllers = [OffboardingExportController::class])
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class OffboardingExportControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var service: StudyExportService

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuthorization: ProjectAuthorization

    private val projectId = UUID.randomUUID()
    private val url = "/api/v1/projects/$projectId/offboarding/export"
    private val asOf = Instant.parse("2026-11-20T12:00:00Z")
    private val snapshotId = UUID.randomUUID()
    private val pmJwt = jwt().jwt { it.subject("pm-id") }.authorities(SimpleGrantedAuthority("ROLE_PM"))
    private val userJwt = jwt().jwt { it.subject("user-id") }.authorities(SimpleGrantedAuthority("ROLE_USER"))

    private fun export() = StudyExportResponse(
        exportedAt = Instant.parse("2026-12-01T09:00:00Z"),
        snapshots = listOf(
            ExportedSnapshotResponse(
                id = snapshotId,
                computedAt = Instant.parse("2026-11-21T08:00:00Z"),
                map = RiskMapResponse(
                    asOf = asOf,
                    settings = RiskSettingsResponse(152.0, 365, 0.75, 0.7, 0.3, "default", mapOf("commits" to 1.0)),
                    departing = listOf("P-0123456789ab"),
                    unknownDeparting = emptyList(),
                    summary = RiskSummaryResponse(
                        files = 4,
                        orphaned = 1,
                        hoarded = 1,
                        alreadyOrphaned = 0,
                        listed = 0,
                    ),
                    files = emptyList(),
                ),
            ),
        ),
    )

    @Test
    fun `a manager gets the pseudonymised export`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns true
        every { service.export(ProjectId(projectId)) } returns export()

        mockMvc
            .perform(get(url).with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.exportedAt").value("2026-12-01T09:00:00Z"))
            .andExpect(jsonPath("$.snapshots[0].id").value(snapshotId.toString()))
            .andExpect(jsonPath("$.snapshots[0].map.departing[0]").value("P-0123456789ab"))
            .andExpect(jsonPath("$.snapshots[0].map.settings.name").value("default"))
    }

    @Test
    fun `someone who does not manage the project is refused and nothing is exported`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns false

        mockMvc.perform(get(url).with(pmJwt)).andExpect(status().isForbidden)

        verify(exactly = 0) { service.export(any()) }
    }

    @Test
    fun `a user without the manager role is refused`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns true

        mockMvc.perform(get(url).with(userJwt)).andExpect(status().isForbidden)
    }

    @Test
    fun `an anonymous request is refused`() {
        mockMvc.perform(get(url)).andExpect(status().isUnauthorized)
    }
}
