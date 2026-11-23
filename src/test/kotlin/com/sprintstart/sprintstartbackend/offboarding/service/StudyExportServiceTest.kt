package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.model.RiskSnapshotId
import com.sprintstart.sprintstartbackend.offboarding.model.SnapshotSummary
import com.sprintstart.sprintstartbackend.offboarding.model.StoredRiskMap
import com.sprintstart.sprintstartbackend.offboarding.model.response.ConcentrationResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.FileRiskResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskMapResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSettingsResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSummaryResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.SuccessorResponse
import com.sprintstart.sprintstartbackend.shared.config.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.config.CryptoConfig
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class StudyExportServiceTest {
    private val project = ProjectId(UUID.randomUUID())
    private val asOf = Instant.parse("2026-11-20T12:00:00Z")
    private val now = Instant.parse("2026-12-01T09:00:00Z")
    private val snapshots = mockk<RiskSnapshotService>()

    private fun factory(secret: String = "master") =
        PseudonymFactory(mockk<ApplicationConfig> { every { crypto } returns CryptoConfig(secret, "salt") })

    private fun service(secret: String = "master") =
        StudyExportService(snapshots, factory(secret), Clock.fixed(now, ZoneOffset.UTC))

    private fun summary(label: String?, computedAt: String) = SnapshotSummary(
        id = RiskSnapshotId(UUID.randomUUID()),
        label = label,
        computedAt = Instant.parse(computedAt),
        asOf = asOf,
        departing = listOf("ada@x.org"),
        profileName = "default",
        files = 4,
        orphaned = 1,
        hoarded = 1,
    )

    private fun file(path: String, vararg knowers: String) = FileRiskResponse(
        repository = "github:acme/app",
        path = path,
        level = "HOARDED",
        score = 0.6,
        departingShare = 0.5,
        sourceBreakdown = mapOf("commits" to 0.5),
        recentActivity = 0.3,
        fragmentation = 0.4,
        concentration = ConcentrationResponse(1.1, knowers.firstOrNull()),
        successors = knowers.map { SuccessorResponse(it, 4.0) },
        remainingKnowers = knowers.toList(),
        lastChangedAt = asOf,
    )

    private fun map(departing: List<String>, unknown: List<String>, vararg files: FileRiskResponse) = RiskMapResponse(
        asOf = asOf,
        settings = RiskSettingsResponse(152.0, 365, 0.75, 0.7, 0.3, "default", mapOf("commits" to 1.0)),
        departing = departing,
        unknownDeparting = unknown,
        summary = RiskSummaryResponse(files = 4, orphaned = 1, hoarded = 1, alreadyOrphaned = 0, listed = files.size),
        files = files.toList(),
    )

    private fun stored(summary: SnapshotSummary, map: RiskMapResponse) {
        every { snapshots.get(project, summary.id) } returns StoredRiskMap(summary, map)
    }

    private val first = summary("Ada leaves, baseline", "2026-11-21T08:00:00Z")
    private val second = summary("Ben takes over", "2026-11-22T08:00:00Z")

    private fun storeTwoMaps() {
        every { snapshots.list(project) } returns listOf(second, first)
        stored(
            first,
            map(listOf("ada@x.org"), listOf("typo@x.org"), file("core.kt", "ben@x.org"), file("util.kt", "carol")),
        )
        stored(second, map(listOf("ada@x.org", "ben@x.org"), emptyList(), file("core.kt", "carol")))
    }

    @Test
    fun `no identity appears anywhere in the exported data`() {
        storeTwoMaps()

        val json = jacksonObjectMapper().writeValueAsString(service().export(project))

        listOf("ada@", "ben@", "carol", "typo", "x.org", "leaves", "takes over").forEach {
            assertThat(json).doesNotContainIgnoringCase(it)
        }
    }

    @Test
    fun `the people are replaced in every place a map names them`() {
        storeTwoMaps()
        val names = factory().forProject(project)

        val exported = service()
            .export(project)
            .snapshots
            .first()
            .map

        assertThat(exported.departing).containsExactly(names.of("ada@x.org"))
        assertThat(exported.unknownDeparting).containsExactly(names.of("typo@x.org"))
        assertThat(exported.files.map { it.remainingKnowers })
            .containsExactly(listOf(names.of("ben@x.org")), listOf(names.of("carol")))
        assertThat(exported.files.map { file -> file.successors.map { it.person } })
            .containsExactly(listOf(names.of("ben@x.org")), listOf(names.of("carol")))
        assertThat(exported.files.map { it.concentration?.island })
            .containsExactly(names.of("ben@x.org"), names.of("carol"))
    }

    @Test
    fun `the same person has the same pseudonym in every map of the project`() {
        storeTwoMaps()

        val maps = service().export(project).snapshots.map { it.map }

        assertThat(maps[1].departing).contains(maps[0].departing.single())
        assertThat(maps[1].files.single().remainingKnowers).isEqualTo(maps[0].files[1].remainingKnowers)
    }

    @Test
    fun `the same person has another pseudonym in another project and under another secret`() {
        storeTwoMaps()
        val here = service()
            .export(project)
            .snapshots
            .first()
            .map.departing
        val other = ProjectId(UUID.randomUUID())
        every { snapshots.list(other) } returns listOf(first)
        every { snapshots.get(other, first.id) } returns StoredRiskMap(first, map(listOf("ada@x.org"), emptyList()))

        assertThat(
            service()
                .export(other)
                .snapshots
                .single()
                .map.departing,
        ).isNotEqualTo(here)
        assertThat(
            service("another master")
                .export(project)
                .snapshots
                .first()
                .map.departing,
        ).isNotEqualTo(here)
    }

    @Test
    fun `maps come oldest first with their settings, counts and files intact`() {
        storeTwoMaps()

        val export = service().export(project)

        assertThat(export.exportedAt).isEqualTo(now)
        assertThat(export.snapshots.map { it.id }).containsExactly(first.id.value, second.id.value)
        assertThat(export.snapshots.map { it.computedAt }).containsExactly(first.computedAt, second.computedAt)
        val exported = export.snapshots.first().map
        assertThat(exported.settings.name).isEqualTo("default")
        assertThat(exported.summary.orphaned).isEqualTo(1)
        assertThat(exported.files.map { it.path }).containsExactly("core.kt", "util.kt")
        assertThat(exported.files.first().score).isEqualTo(0.6)
        assertThat(exported.files.first().sourceBreakdown).containsEntry("commits", 0.5)
    }

    @Test
    fun `a project without stored maps exports nothing`() {
        every { snapshots.list(project) } returns emptyList()

        assertThat(service().export(project).snapshots).isEmpty()
    }
}
