package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskLevel
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskScorer
import com.sprintstart.sprintstartbackend.offboarding.scoring.ScoringProfile
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class OffboardingRiskServiceTest {
    private val now = Instant.parse("2026-11-20T12:00:00Z")
    private val project = ProjectId(UUID.randomUUID())

    private class RecordingEvidence(
        private val rows: List<FileContribution>,
    ) : ContributionEvidenceApi {
        val asked = mutableListOf<Pair<ProjectId, Instant>>()

        override fun fileContributions(projectId: ProjectId, asOf: Instant): List<FileContribution> {
            asked += projectId to asOf
            return rows.filter { !it.changedAt.isAfter(asOf) }
        }
    }

    private fun row(path: String, email: String, at: String) =
        FileContribution("github:acme/app", path, null, email, Instant.parse(at))

    private val rows = listOf(
        row("core.kt", "ada@x.org", "2026-09-01T00:00:00Z"),
        row("core.kt", "ada@x.org", "2026-10-01T00:00:00Z"),
        row("late.kt", "ada@x.org", "2026-11-10T00:00:00Z"),
    )

    private fun service(evidence: ContributionEvidenceApi) =
        OffboardingRiskService(evidence, RiskScorer(), Clock.fixed(now, ZoneOffset.UTC))

    private fun request(asOf: Instant? = null) =
        RiskRequest(setOf(PersonKey("ada@x.org")), asOf, ScoringProfile())

    @Test
    fun `an open date means the moment of the request`() {
        val evidence = RecordingEvidence(rows)

        val map = service(evidence).riskMap(project, request())

        assertThat(evidence.asked).containsExactly(project to now)
        assertThat(map.asOf).isEqualTo(now)
        assertThat(map.files.map { it.path }).containsExactlyInAnyOrder("core.kt", "late.kt")
    }

    @Test
    fun `a given date is replayed and later history stays out`() {
        val evidence = RecordingEvidence(rows)
        val earlier = Instant.parse("2026-10-15T00:00:00Z")

        val map = service(evidence).riskMap(project, request(asOf = earlier))

        assertThat(evidence.asked).containsExactly(project to earlier)
        assertThat(map.asOf).isEqualTo(earlier)
        assertThat(map.files.map { it.path }).containsExactly("core.kt")
        assertThat(map.files.single().level).isEqualTo(RiskLevel.ORPHANED)
    }

    @Test
    fun `a project without recorded history has an empty map`() {
        val map = service(RecordingEvidence(emptyList())).riskMap(project, request())

        assertThat(map.files).isEmpty()
        assertThat(map.summary.files).isZero()
        assertThat(map.unknownDeparting).containsExactly(PersonKey("ada@x.org"))
    }
}
