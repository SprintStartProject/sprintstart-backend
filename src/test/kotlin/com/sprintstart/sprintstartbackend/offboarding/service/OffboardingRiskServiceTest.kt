package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileComplexityReading
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.scoring.AliasLink
import com.sprintstart.sprintstartbackend.offboarding.scoring.CommitEvidence
import com.sprintstart.sprintstartbackend.offboarding.scoring.EvidenceSource
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileHistory
import com.sprintstart.sprintstartbackend.offboarding.scoring.Identities
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskLevel
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskScorer
import com.sprintstart.sprintstartbackend.offboarding.scoring.SourceId
import com.sprintstart.sprintstartbackend.offboarding.scoring.Touch
import com.sprintstart.sprintstartbackend.offboarding.scoring.WeightProfile
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class OffboardingRiskServiceTest {
    private val now = Instant.parse("2026-11-20T12:00:00Z")
    private val project = ProjectId(UUID.randomUUID())
    private val links = mockk<IdentityLinkService> { every { identities(project) } returns Identities() }

    private class RecordingEvidence(
        private val rows: List<FileContribution>,
    ) : ContributionEvidenceApi {
        val asked = mutableListOf<Pair<ProjectId, Instant>>()

        override fun fileContributions(projectId: ProjectId, asOf: Instant): List<FileContribution> {
            asked += projectId to asOf
            return rows.filter { !it.changedAt.isAfter(asOf) }
        }

        override fun fileReviews(projectId: ProjectId, asOf: Instant): List<FileReview> = emptyList()

        override fun fileComplexities(repositories: Collection<String>): List<FileComplexityReading> = emptyList()
    }

    private fun row(path: String, email: String, at: String) =
        FileContribution("github:acme/app", path, null, email, Instant.parse(at))

    private val rows = listOf(
        row("core.kt", "ada@x.org", "2026-09-01T00:00:00Z"),
        row("core.kt", "ada@x.org", "2026-10-01T00:00:00Z"),
        row("late.kt", "ada@x.org", "2026-11-10T00:00:00Z"),
    )

    private fun service(evidence: ContributionEvidenceApi) =
        OffboardingRiskService(listOf(CommitEvidence(evidence)), links, RiskScorer(), Clock.fixed(now, ZoneOffset.UTC))

    private fun request(asOf: Instant? = null) =
        RiskRequest(setOf(PersonKey("ada@x.org")), asOf, WeightProfile())

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

    private class FixedSource(
        override val id: SourceId,
        private val histories: List<FileHistory>,
    ) : EvidenceSource {
        var reads = 0

        override fun histories(projectId: ProjectId, asOf: Instant): List<FileHistory> {
            reads++
            return histories
        }
    }

    private fun reviewOf(path: String, email: String) = FileHistory(
        "github:acme/app",
        path,
        listOf(
            Touch(
                PersonKey.ofEmail(email),
                Instant.parse("2026-10-20T00:00:00Z"),
                SourceId("reviews"),
                founding = false,
            ),
        ),
    )

    @Test
    fun `a source the profile gives no weight is not read`() {
        val reviews = FixedSource(SourceId("reviews"), listOf(reviewOf("core.kt", "ada@x.org")))
        val commits = CommitEvidence(RecordingEvidence(rows))
        val service =
            OffboardingRiskService(listOf(commits, reviews), links, RiskScorer(), Clock.fixed(now, ZoneOffset.UTC))

        service.riskMap(project, request())

        assertThat(reviews.reads).isZero()
    }

    @Test
    fun `sources the profile weighs are merged into one ranking`() {
        val reviews = FixedSource(SourceId("reviews"), listOf(reviewOf("core.kt", "ada@x.org")))
        val commits = CommitEvidence(RecordingEvidence(rows))
        val service =
            OffboardingRiskService(listOf(commits, reviews), links, RiskScorer(), Clock.fixed(now, ZoneOffset.UTC))
        val profile = WeightProfile(sourceWeights = mapOf(SourceId.COMMITS to 1.0, SourceId("reviews") to 1.0))

        val map = service.riskMap(project, RiskRequest(setOf(PersonKey("ada@x.org")), null, profile))

        assertThat(reviews.reads).isEqualTo(1)
        assertThat(
            map.files
                .single { it.path == "core.kt" }
                .sourceBreakdown.keys,
        ).containsExactlyInAnyOrder(SourceId.COMMITS, SourceId("reviews"))
    }

    @Test
    fun `confirmed links make a developer's identities count as one person`() {
        val reviews = FixedSource(SourceId("reviews"), listOf(reviewOf("core.kt", "ada")))
        val commits = CommitEvidence(RecordingEvidence(rows))
        val service =
            OffboardingRiskService(listOf(commits, reviews), links, RiskScorer(), Clock.fixed(now, ZoneOffset.UTC))
        val profile = WeightProfile(sourceWeights = mapOf(SourceId.COMMITS to 1.0, SourceId("reviews") to 1.0))
        val request = RiskRequest(setOf(PersonKey("ada")), null, profile)

        val unlinked = service.riskMap(project, request)
        every { links.identities(project) } returns
            Identities(listOf(AliasLink(PersonKey("ada"), PersonKey("ada@x.org"))))
        val linked = service.riskMap(project, request)

        assertThat(unlinked.files).isEmpty()
        assertThat(linked.departing).containsExactly(PersonKey("ada@x.org"))
        assertThat(linked.unknownDeparting).isEmpty()
        assertThat(linked.files.map { it.path }).contains("core.kt")
    }

    @Test
    fun `a project without recorded history has an empty map`() {
        val map = service(RecordingEvidence(emptyList())).riskMap(project, request())

        assertThat(map.files).isEmpty()
        assertThat(map.summary.files).isZero()
        assertThat(map.unknownDeparting).containsExactly(PersonKey("ada@x.org"))
    }
}
