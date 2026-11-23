package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.model.RiskSnapshotId
import com.sprintstart.sprintstartbackend.offboarding.model.exception.RiskSnapshotNotFoundException
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.repository.RiskSnapshotRepository
import com.sprintstart.sprintstartbackend.offboarding.scoring.Concentration
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileRisk
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskLevel
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskMap
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskSummary
import com.sprintstart.sprintstartbackend.offboarding.scoring.SourceId
import com.sprintstart.sprintstartbackend.offboarding.scoring.WeightProfile
import com.sprintstart.sprintstartbackend.shared.crypto.CryptoConfiguration
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

/** A stored ranking is a row with a payload and a project key, so the rules about it meet a real database. */
@ActiveProfiles("test")
@DataJpaTest
@Import(CryptoConfiguration::class)
class RiskSnapshotServiceTest {
    @Autowired
    private lateinit var repository: RiskSnapshotRepository

    private val project = ProjectId(UUID.randomUUID())
    private val otherProject = ProjectId(UUID.randomUUID())
    private val asOf = Instant.parse("2026-11-20T12:00:00Z")
    private val risks = mockk<OffboardingRiskService>()
    private var now = Instant.parse("2026-11-21T08:00:00Z")

    private fun service() = RiskSnapshotService(
        repository,
        risks,
        jacksonObjectMapper(),
        object : Clock() {
            override fun getZone() = ZoneOffset.UTC

            override fun withZone(zone: java.time.ZoneId) = this

            override fun instant(): Instant = now
        },
    )

    private fun risk(path: String, level: RiskLevel, score: Double) = FileRisk(
        repository = "github:acme/app",
        path = path,
        level = level,
        score = score,
        departingShare = 1.0,
        sourceBreakdown = mapOf(SourceId.COMMITS to 1.0),
        recentActivity = 0.4,
        fragmentation = 0.0,
        concentration = Concentration(1.0, null),
        remainingKnowers = if (level == RiskLevel.HOARDED) listOf(PersonKey("ben")) else emptyList(),
        lastChangedAt = asOf,
    )

    private fun map(vararg files: FileRisk, profile: WeightProfile = WeightProfile()) = RiskMap(
        asOf = asOf,
        profile = profile,
        departing = setOf(PersonKey("ada"), PersonKey("zed@x.org")),
        unknownDeparting = emptySet(),
        summary = RiskSummary(files = 12, orphaned = 1, hoarded = 1, alreadyOrphaned = 0),
        files = files.toList(),
    )

    private val request = RiskRequest(setOf(PersonKey("ada")), asOf, WeightProfile())

    private fun computes(map: RiskMap, forProject: ProjectId = project) {
        every { risks.riskMap(forProject, any()) } returns map
    }

    @Test
    fun `computing stores the whole ranking with its settings and the moment it was computed`() {
        computes(map(risk("core.kt", RiskLevel.ORPHANED, 0.9), risk("util.kt", RiskLevel.HOARDED, 0.5)))

        val stored = service().create(project, request, "baseline")

        assertThat(stored.summary.label).isEqualTo("baseline")
        assertThat(stored.summary.computedAt).isEqualTo(now)
        assertThat(stored.summary.asOf).isEqualTo(asOf)
        assertThat(stored.summary.profileName).isEqualTo("default")
        assertThat(stored.summary.departing).containsExactlyInAnyOrder("ada", "zed@x.org")
        assertThat(stored.summary.files).isEqualTo(12)
        assertThat(stored.summary.orphaned).isEqualTo(1)
        assertThat(stored.summary.hoarded).isEqualTo(1)
        assertThat(stored.map.files.map { it.path }).containsExactly("core.kt", "util.kt")
    }

    @Test
    fun `reading a stored map gives back the ranking as it was served`() {
        computes(map(risk("core.kt", RiskLevel.ORPHANED, 0.9), risk("util.kt", RiskLevel.HOARDED, 0.5)))
        val created = service().create(project, request, null)

        val read = service().get(project, created.summary.id)

        assertThat(read.map).isEqualTo(created.map)
        assertThat(read.map.files[1].remainingKnowers).containsExactly("ben")
        assertThat(read.map.files[0].sourceBreakdown).containsEntry("commits", 1.0)
        assertThat(read.map.asOf).isEqualTo(asOf)
        assertThat(read.summary).isEqualTo(created.summary)
    }

    @Test
    fun `a blank label counts as none and a label is trimmed`() {
        computes(map())

        assertThat(service().create(project, request, "  ").summary.label).isNull()
        assertThat(service().create(project, request, null).summary.label).isNull()
        assertThat(service().create(project, request, " mine ").summary.label).isEqualTo("mine")
    }

    @Test
    fun `listing shows a project's maps newest first and nobody else's`() {
        computes(map())
        every { risks.riskMap(otherProject, any()) } returns map()
        now = Instant.parse("2026-11-21T08:00:00Z")
        val older = service().create(project, request, "older").summary.id
        now = Instant.parse("2026-11-22T08:00:00Z")
        val newer = service().create(project, request, "newer").summary.id
        service().create(otherProject, request, "theirs")

        val listed = service().list(project)

        assertThat(listed.map { it.id }).containsExactly(newer, older)
        assertThat(listed.map { it.label }).containsExactly("newer", "older")
        assertThat(service().list(ProjectId(UUID.randomUUID()))).isEmpty()
    }

    @Test
    fun `a project cannot read or delete another project's map`() {
        computes(map(risk("core.kt", RiskLevel.ORPHANED, 0.9)))
        val mine = service().create(project, request, null).summary.id

        assertThrows<RiskSnapshotNotFoundException> { service().get(otherProject, mine) }
        assertThrows<RiskSnapshotNotFoundException> { service().delete(otherProject, mine) }
        assertThat(service().get(project, mine).map.files).hasSize(1)
    }

    @Test
    fun `deleting a map removes only that map`() {
        computes(map())
        val first = service().create(project, request, "first").summary.id
        val second = service().create(project, request, "second").summary.id

        service().delete(project, first)

        assertThat(service().list(project).map { it.id }).containsExactly(second)
        assertThrows<RiskSnapshotNotFoundException> { service().delete(project, first) }
    }

    @Test
    fun `an unknown map is not found`() {
        assertThrows<RiskSnapshotNotFoundException> { service().get(project, RiskSnapshotId(UUID.randomUUID())) }
    }

    @Test
    fun `comparing two stored maps compares their stored rankings`() {
        computes(
            map(
                risk("a", RiskLevel.ORPHANED, 0.9),
                risk("b", RiskLevel.ORPHANED, 0.8),
                risk("c", RiskLevel.HOARDED, 0.5),
            ),
        )
        val commits = service().create(project, request, "commits only").summary.id
        computes(
            map(
                risk("b", RiskLevel.ORPHANED, 0.9),
                risk("a", RiskLevel.ORPHANED, 0.8),
                risk("d", RiskLevel.HOARDED, 0.5),
            ),
        )
        val withReviews = service().create(project, request, "with reviews").summary.id

        val compared = service().compare(project, commits, withReviews)

        assertThat(compared.left.label).isEqualTo("commits only")
        assertThat(compared.right.label).isEqualTo("with reviews")
        assertThat(compared.comparison.both).isEqualTo(2)
        assertThat(compared.comparison.onlyLeft).isEqualTo(1)
        assertThat(compared.comparison.onlyRight).isEqualTo(1)
        assertThat(compared.comparison.rankCorrelation).isEqualTo(-1.0)
    }

    @Test
    fun `comparing needs both maps to exist in the project`() {
        computes(map())
        val mine = service().create(project, request, null).summary.id

        assertThrows<RiskSnapshotNotFoundException> {
            service().compare(project, mine, RiskSnapshotId(UUID.randomUUID()))
        }
        assertThrows<RiskSnapshotNotFoundException> { service().compare(otherProject, mine, mine) }
    }
}
