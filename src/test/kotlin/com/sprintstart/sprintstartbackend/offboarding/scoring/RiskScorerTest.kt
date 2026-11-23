package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.time.Instant

class RiskScorerTest {
    private val scorer = RiskScorer()

    private fun departure(
        vararg leaving: String,
        asOf: Instant = NOW,
        profile: ScoringProfile = ScoringProfile(),
    ) = DepartureQuery(leaving.map(PersonKey::parse).toSet(), asOf, profile)

    private fun solo(path: String, email: String, firstAgo: Long = 300, times: Int = 3) =
        history(path, *repeated(email, times, firstAgo))

    /** Ada and Ben maintain the file about equally, so both are authors with the default profile. */
    private fun pair(path: String) =
        history(path, *repeated("ada@x.org", 5, 60), *repeated("ben@x.org", 5, 30))

    private fun RiskMap.paths() = files.map { it.path }

    @Test
    fun `a file only the leaving person knows is orphaned`() {
        val map = scorer.score(listOf(solo("core.kt", "ada@x.org")), departure("ada@x.org"))

        val risk = map.files.single()

        assertThat(risk.level).isEqualTo(RiskLevel.ORPHANED)
        assertThat(risk.remainingKnowers).isEmpty()
        assertThat(risk.departingShare).isEqualTo(1.0)
    }

    @Test
    fun `a file with one author left after the departure is hoarded and names who stays`() {
        val map = scorer.score(listOf(pair("util.kt")), departure("ada@x.org"))

        val risk = map.files.single()

        assertThat(risk.level).isEqualTo(RiskLevel.HOARDED)
        assertThat(risk.remainingKnowers).containsExactly(PersonKey("ben@x.org"))
    }

    @Test
    fun `the leaving person's share and the file's fragmentation are measured on all its changes`() {
        val risk = scorer.score(listOf(pair("util.kt")), departure("ada@x.org")).files.single()

        assertThat(risk.departingShare).isEqualTo(0.5)
        assertThat(risk.fragmentation).isEqualTo(0.5)
    }

    @Test
    fun `a file that keeps two or more authors is not at risk`() {
        val noFading = ScoringProfile(halfLifeDays = 1_000_000.0)
        val shared = history(
            "shared.kt",
            *repeated("ada@x.org", 10, 90),
            *repeated("ben@x.org", 10, 70),
            *repeated("cy@x.org", 10, 50),
        )

        val map = scorer.score(listOf(shared), departure("ada@x.org", profile = noFading))

        assertThat(map.files).isEmpty()
        assertThat(map.summary).isEqualTo(RiskSummary(files = 1, orphaned = 0, hoarded = 0, alreadyOrphaned = 0))
    }

    @Test
    fun `a file the leaving person never touched is not at risk`() {
        val map = scorer.score(listOf(solo("other.kt", "ben@x.org")), departure("ada@x.org"))

        assertThat(map.files).isEmpty()
    }

    @Test
    fun `a file nobody active knew is counted as already orphaned and not listed`() {
        val forgotten = solo("forgotten.kt", "old@x.org", firstAgo = 900)

        val map = scorer.score(listOf(forgotten, solo("core.kt", "ada@x.org")), departure("ada@x.org"))

        assertThat(map.summary.alreadyOrphaned).isEqualTo(1)
        assertThat(map.paths()).containsExactly("core.kt")
    }

    @Test
    fun `a person who has been inactive for long no longer counts as someone who stays`() {
        val oldCreator = history(
            "legacy.kt",
            *repeated("old@x.org", 5, 800),
            *repeated("ada@x.org", 5, 400),
        )
        val adaStaysActive = solo("recent.kt", "ada@x.org", firstAgo = 12)

        val map = scorer.score(listOf(oldCreator, adaStaysActive), departure("ada@x.org"))

        val legacy = map.files.single { it.path == "legacy.kt" }
        assertThat(legacy.level).isEqualTo(RiskLevel.ORPHANED)
        assertThat(legacy.remainingKnowers).isEmpty()
    }

    @Test
    fun `the inactivity limit decides who still counts as active`() {
        val file = history("legacy.kt", *repeated("old@x.org", 5, 800), *repeated("ada@x.org", 5, 400))
        val adaStaysActive = solo("recent.kt", "ada@x.org", firstAgo = 12)
        val patient = ScoringProfile(inactiveAfterDays = 3_000)

        val map = scorer.score(listOf(file, adaStaysActive), departure("ada@x.org", profile = patient))

        assertThat(map.files.single { it.path == "legacy.kt" }.level).isEqualTo(RiskLevel.HOARDED)
    }

    @Test
    fun `replaying an earlier date ignores later changes`() {
        val file = history("util.kt", *repeated("ada@x.org", 5, 200), *repeated("ben@x.org", 5, 60))

        val today = scorer.score(listOf(file), departure("ada@x.org"))
        val earlier = scorer.score(listOf(file), departure("ada@x.org", asOf = daysAgo(100)))

        assertThat(today.files.single().level).isEqualTo(RiskLevel.HOARDED)
        assertThat(earlier.files.single().level).isEqualTo(RiskLevel.ORPHANED)
    }

    @Test
    fun `a file that did not exist yet on the date is not counted`() {
        val map = scorer.score(
            listOf(solo("new.kt", "ada@x.org", firstAgo = 10)),
            departure("ada@x.org", asOf = daysAgo(100)),
        )

        assertThat(map.summary.files).isZero()
    }

    @Test
    fun `a leaving person without any recorded change is reported as unknown`() {
        val map = scorer.score(listOf(solo("core.kt", "ada@x.org")), departure("ghost@x.org", "ada@x.org"))

        assertThat(map.unknownDeparting).containsExactly(PersonKey("ghost@x.org"))
    }

    @Test
    fun `several leaving people orphan a file their departures would only thin out one by one`() {
        val one = scorer.score(listOf(pair("util.kt")), departure("ada@x.org"))
        val both = scorer.score(listOf(pair("util.kt")), departure("ada@x.org", "ben@x.org"))

        assertThat(one.files.single().level).isEqualTo(RiskLevel.HOARDED)
        assertThat(both.files.single().level).isEqualTo(RiskLevel.ORPHANED)
    }

    @Test
    fun `orphaned files come before hoarded ones and busier files before quieter ones`() {
        val busy = solo("busy.kt", "ada@x.org", firstAgo = 30, times = 10)
        val quiet = solo("quiet.kt", "ada@x.org", firstAgo = 400, times = 2)

        val map = scorer.score(listOf(quiet, pair("util.kt"), busy), departure("ada@x.org"))

        assertThat(map.paths()).containsExactly("busy.kt", "quiet.kt", "util.kt")
    }

    @Test
    fun `files that rank equally are ordered by path`() {
        val files = listOf(solo("b.kt", "ada@x.org"), solo("a.kt", "ada@x.org"))

        val map = scorer.score(files, departure("ada@x.org"))

        assertThat(map.paths()).containsExactly("a.kt", "b.kt")
    }

    @Test
    fun `the busiest file has full recent activity and a quiet one close to none`() {
        val busy = solo("busy.kt", "ada@x.org", firstAgo = 30, times = 10)
        val quiet = solo("quiet.kt", "ada@x.org", firstAgo = 900, times = 1)

        val map = scorer.score(listOf(busy, quiet), departure("ada@x.org"))

        assertThat(map.files.first { it.path == "busy.kt" }.recentActivity).isEqualTo(1.0)
        assertThat(map.files.first { it.path == "quiet.kt" }.recentActivity).isLessThan(0.01)
    }

    @Test
    fun `the weights decide how knowledge and activity make up the score`() {
        val files = listOf(solo("busy.kt", "ada@x.org", firstAgo = 30, times = 10), pair("util.kt"))
        val onlyActivity = ScoringProfile(knowledgeWeight = 0.0, activityWeight = 1.0)
        val onlyKnowledge = ScoringProfile(knowledgeWeight = 1.0, activityWeight = 0.0)

        val byActivity = scorer.score(files, departure("ada@x.org", profile = onlyActivity))
        val byKnowledge = scorer.score(files, departure("ada@x.org", profile = onlyKnowledge))

        byActivity.files.forEach { assertThat(it.score).isEqualTo(it.recentActivity, within(1e-12)) }
        byKnowledge.files.forEach { assertThat(it.score).isEqualTo(it.departingShare, within(1e-12)) }
    }

    @Test
    fun `the summary counts every class of file`() {
        val files = listOf(
            solo("core.kt", "ada@x.org"),
            pair("util.kt"),
            solo("other.kt", "ben@x.org"),
            solo("forgotten.kt", "old@x.org", firstAgo = 900),
        )

        val map = scorer.score(files, departure("ada@x.org"))

        assertThat(map.summary).isEqualTo(RiskSummary(files = 4, orphaned = 1, hoarded = 1, alreadyOrphaned = 1))
    }

    @Test
    fun `the same departure on the same date always gives the same map`() {
        val files = listOf(solo("core.kt", "ada@x.org"), pair("util.kt"))

        assertThat(scorer.score(files, departure("ada@x.org"))).isEqualTo(scorer.score(files, departure("ada@x.org")))
    }

    @Test
    fun `the map records the settings and the date it was computed with`() {
        val profile = ScoringProfile(halfLifeDays = 30.0)

        val map = scorer.score(emptyList(), departure("ada@x.org", asOf = daysAgo(5), profile = profile))

        assertThat(map.profile).isEqualTo(profile)
        assertThat(map.asOf).isEqualTo(daysAgo(5))
        assertThat(map.files).isEmpty()
    }

    @Test
    fun `the last change of a file is shown`() {
        val risk = scorer
            .score(
                listOf(solo("core.kt", "ada@x.org", firstAgo = 30, times = 3)),
                departure("ada@x.org"),
            ).files
            .single()

        assertThat(risk.lastChangedAt).isEqualTo(daysAgo(28))
    }
}
