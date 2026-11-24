package com.sprintstart.sprintstartbackend.offboarding.controller

import com.ninjasquad.springmockk.MockkBean
import com.sprintstart.sprintstartbackend.config.SecurityConfig
import com.sprintstart.sprintstartbackend.offboarding.model.RiskSnapshotId
import com.sprintstart.sprintstartbackend.offboarding.model.SnapshotSummary
import com.sprintstart.sprintstartbackend.offboarding.model.StoredComparison
import com.sprintstart.sprintstartbackend.offboarding.model.StoredRiskMap
import com.sprintstart.sprintstartbackend.offboarding.model.exception.RiskSnapshotNotFoundException
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.model.response.FileRiskResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskMapResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSettingsResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSummaryResponse
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileMovement
import com.sprintstart.sprintstartbackend.offboarding.scoring.MapComparison
import com.sprintstart.sprintstartbackend.offboarding.service.RiskSnapshotService
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.user.external.security.ProjectAuthorization
import io.mockk.every
import io.mockk.just
import io.mockk.runs
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.util.UUID

@WebMvcTest(controllers = [OffboardingSnapshotController::class])
@AutoConfigureMockMvc
@Import(SecurityConfig::class)
class OffboardingSnapshotControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var service: RiskSnapshotService

    @MockkBean(name = "projectAuth")
    private lateinit var projectAuthorization: ProjectAuthorization

    private val projectId = UUID.randomUUID()
    private val project = ProjectId(projectId)
    private val base = "/api/v1/projects/$projectId/offboarding/risk/snapshots"
    private val asOf = Instant.parse("2026-11-20T12:00:00Z")
    private val snapshotId = UUID.randomUUID()
    private val pmJwt = jwt().jwt { it.subject("pm-id") }.authorities(SimpleGrantedAuthority("ROLE_PM"))
    private val userJwt = jwt().jwt { it.subject("user-id") }.authorities(SimpleGrantedAuthority("ROLE_USER"))

    private val summary = SnapshotSummary(
        id = RiskSnapshotId(snapshotId),
        label = "baseline",
        computedAt = Instant.parse("2026-11-21T08:00:00Z"),
        asOf = asOf,
        departing = listOf("ada"),
        profileName = "default",
        files = 12,
        orphaned = 2,
        hoarded = 1,
    )

    private fun fileRisk(path: String) = FileRiskResponse(
        repository = "github:acme/app",
        path = path,
        level = "ORPHANED",
        score = 0.8,
        departingShare = 1.0,
        sourceBreakdown = mapOf("commits" to 1.0),
        recentActivity = 0.4,
        fragmentation = 0.0,
        remainingKnowers = emptyList(),
        lastChangedAt = asOf,
    )

    private fun stored() = StoredRiskMap(
        summary,
        RiskMapResponse(
            asOf = asOf,
            settings = RiskSettingsResponse(152.0, 365, 0.75, 0.7, 0.3, "default", mapOf("commits" to 1.0)),
            departing = listOf("ada"),
            unknownDeparting = emptyList(),
            summary = RiskSummaryResponse(files = 12, orphaned = 2, hoarded = 1, alreadyOrphaned = 0, listed = 3),
            files = listOf(fileRisk("a.kt"), fileRisk("b.kt"), fileRisk("c.kt")),
        ),
    )

    private fun allowManager() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns true
    }

    @Test
    fun `computing a map stores it and answers created with the ranking`() {
        allowManager()
        val request = slot<RiskRequest>()
        every { service.create(project, capture(request), "baseline") } returns stored()

        mockMvc
            .perform(
                post(base)
                    .param("leaving", "ada")
                    .param("reviewWeight", "0.5")
                    .param("label", "baseline")
                    .with(pmJwt),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.snapshot.id").value(snapshotId.toString()))
            .andExpect(jsonPath("$.snapshot.label").value("baseline"))
            .andExpect(jsonPath("$.map.files.length()").value(3))

        assertThat(request.captured.profile.sourceWeights.values).containsExactlyInAnyOrder(1.0, 0.5)
    }

    @Test
    fun `computing without anybody leaving is a bad request and stores nothing`() {
        allowManager()

        mockMvc.perform(post(base).with(pmJwt)).andExpect(status().isBadRequest)

        verify(exactly = 0) { service.create(any(), any(), any()) }
    }

    @Test
    fun `a manager lists the stored maps`() {
        allowManager()
        every { service.list(project) } returns listOf(summary)

        mockMvc
            .perform(get(base).with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].id").value(snapshotId.toString()))
            .andExpect(jsonPath("$[0].profileName").value("default"))
            .andExpect(jsonPath("$[0].orphaned").value(2))
            .andExpect(jsonPath("$[0].departing[0]").value("ada"))
    }

    @Test
    fun `reading a map gives its ranking and the limit shortens the list`() {
        allowManager()
        every { service.get(project, RiskSnapshotId(snapshotId)) } returns stored()

        mockMvc
            .perform(get("$base/$snapshotId").param("limit", "2").with(pmJwt))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.snapshot.label").value("baseline"))
            .andExpect(jsonPath("$.map.files.length()").value(2))
            .andExpect(jsonPath("$.map.summary.listed").value(2))
            .andExpect(jsonPath("$.map.summary.files").value(12))
    }

    @Test
    fun `a map that does not exist is not found`() {
        allowManager()
        every { service.get(project, RiskSnapshotId(snapshotId)) } throws RiskSnapshotNotFoundException("x")

        mockMvc.perform(get("$base/$snapshotId").with(pmJwt)).andExpect(status().isNotFound)
    }

    @Test
    fun `deleting a map answers no content and an unknown one is not found`() {
        allowManager()
        every { service.delete(project, RiskSnapshotId(snapshotId)) } just runs

        mockMvc.perform(delete("$base/$snapshotId").with(pmJwt)).andExpect(status().isNoContent)

        val missing = UUID.randomUUID()
        every { service.delete(project, RiskSnapshotId(missing)) } throws RiskSnapshotNotFoundException("x")
        mockMvc.perform(delete("$base/$missing").with(pmJwt)).andExpect(status().isNotFound)
    }

    @Test
    fun `two maps are compared and the movements are limited`() {
        allowManager()
        val other = UUID.randomUUID()
        every { service.compare(project, RiskSnapshotId(snapshotId), RiskSnapshotId(other)) } returns StoredComparison(
            left = summary,
            right = summary.copy(id = RiskSnapshotId(other), label = "with reviews"),
            comparison = MapComparison(
                onlyLeft = 1,
                onlyRight = 2,
                both = 5,
                topOverlap = 0.66666,
                rankCorrelation = 0.91234,
                movements = listOf(
                    FileMovement("github:acme/app", "a.kt", 1, 3, 0.9123, 0.4, "ORPHANED", "HOARDED"),
                    FileMovement("github:acme/app", "b.kt", null, 2, null, 0.3, null, "HOARDED"),
                ),
            ),
        )

        mockMvc
            .perform(
                get(
                    "$base/compare",
                ).param("left", snapshotId.toString()).param("right", other.toString()).param("limit", "1").with(pmJwt),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.left.label").value("baseline"))
            .andExpect(jsonPath("$.right.label").value("with reviews"))
            .andExpect(jsonPath("$.onlyLeft").value(1))
            .andExpect(jsonPath("$.onlyRight").value(2))
            .andExpect(jsonPath("$.both").value(5))
            .andExpect(jsonPath("$.topOverlap").value(0.667))
            .andExpect(jsonPath("$.rankCorrelation").value(0.912))
            .andExpect(jsonPath("$.movements.length()").value(1))
            .andExpect(jsonPath("$.movements[0].path").value("a.kt"))
            .andExpect(jsonPath("$.movements[0].leftScore").value(0.912))
    }

    @Test
    fun `comparing needs both maps`() {
        allowManager()

        mockMvc
            .perform(get("$base/compare").param("left", snapshotId.toString()).with(pmJwt))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `someone who does not manage the project is refused everywhere`() {
        every { projectAuthorization.canManageProject(any(), projectId) } returns false

        mockMvc.perform(get(base).with(pmJwt)).andExpect(status().isForbidden)
        mockMvc.perform(post(base).param("leaving", "ada").with(pmJwt)).andExpect(status().isForbidden)
        mockMvc.perform(get("$base/$snapshotId").with(pmJwt)).andExpect(status().isForbidden)
        mockMvc.perform(delete("$base/$snapshotId").with(pmJwt)).andExpect(status().isForbidden)

        verify(exactly = 0) { service.create(any(), any(), any()) }
        verify(exactly = 0) { service.delete(any(), any()) }
    }

    @Test
    fun `a user without the manager role is refused`() {
        allowManager()

        mockMvc.perform(get(base).with(userJwt)).andExpect(status().isForbidden)
    }

    @Test
    fun `an anonymous request is refused`() {
        mockMvc.perform(get(base)).andExpect(status().isUnauthorized)
    }
}
