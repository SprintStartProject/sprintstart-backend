package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReasonsTest {
    private fun risk(
        level: RiskLevel = RiskLevel.ORPHANED,
        share: Double = 1.0,
        island: PersonKey? = null,
        activity: Double = 0.1,
        changedDaysAgo: Long = 2,
        coupling: Coupling = Coupling.NONE,
        hotspot: Double? = null,
    ) = FileRisk(
        repository = REPOSITORY,
        path = "a.kt",
        level = level,
        score = 0.5,
        departingShare = share,
        sourceBreakdown = mapOf(SourceId.COMMITS to share),
        recentActivity = activity,
        fragmentation = 0.0,
        concentration = Concentration(1.0, island),
        remainingKnowers = emptyList(),
        lastChangedAt = daysAgo(changedDaysAgo),
        coupling = coupling,
        hotspot = hotspot,
    )

    private fun reasons(risk: FileRisk) = Reasons.of(risk, NOW)

    @Test
    fun `a file nobody who knows it is left says so`() {
        assertThat(reasons(risk(RiskLevel.ORPHANED))).contains("Nobody who knows it is left.")
    }

    @Test
    fun `a file with one knower left says so`() {
        assertThat(reasons(risk(RiskLevel.HOARDED))).contains("Only one person who knows it is left.")
    }

    @Test
    fun `the share of the leaving people is a rounded percentage`() {
        assertThat(reasons(risk(share = 0.724))).contains("72% of the evidence about it comes from the leaving people.")
    }

    @Test
    fun `an island is named as one person holding almost all of it`() {
        assertThat(reasons(risk(island = PersonKey("ada@x.org"))))
            .contains("One person holds almost all of the knowledge of it.")
        assertThat(reasons(risk())).noneMatch { it.contains("holds almost all") }
    }

    @Test
    fun `a busy file says so and a quiet one does not`() {
        assertThat(reasons(risk(activity = 0.5))).contains("It is among the more active files.")
        assertThat(reasons(risk(activity = 0.49))).noneMatch { it.contains("active") }
    }

    @Test
    fun `the age of the last change is said in months`() {
        assertThat(reasons(risk(changedDaysAgo = 5))).contains("It changed within the last month.")
        assertThat(reasons(risk(changedDaysAgo = 45))).contains("It last changed a month ago.")
        assertThat(reasons(risk(changedDaysAgo = 420))).contains("It last changed 14 months ago.")
    }

    @Test
    fun `the strongest partner is named by its path`() {
        val coupling =
            Coupling(1.5, listOf(CoupledFile(REPOSITORY, "src/Parser.kt", 0.9), CoupledFile(REPOSITORY, "b.kt", 0.6)))

        assertThat(reasons(risk(coupling = coupling))).contains("It usually changes together with src/Parser.kt.")
        assertThat(reasons(risk())).noneMatch { it.contains("together") }
    }

    @Test
    fun `a hotspot says so and a file without one or below half does not`() {
        assertThat(reasons(risk(hotspot = 0.5))).contains("It is a hotspot: complicated and changed often.")
        assertThat(reasons(risk(hotspot = 0.49))).noneMatch { it.contains("hotspot") }
        assertThat(reasons(risk(hotspot = null))).noneMatch { it.contains("hotspot") }
    }

    @Test
    fun `no sentence names a person`() {
        val all = reasons(risk(island = PersonKey("ada@x.org"), activity = 0.9))

        assertThat(all).noneMatch { it.contains("ada") || it.contains("@") }
    }
}
