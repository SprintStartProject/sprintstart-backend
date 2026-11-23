package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class EvidenceSourcesTest {
    private val scorer = RiskScorer()

    private val bothSources = WeightProfile(sourceWeights = mapOf(SourceId.COMMITS to 1.0, REVIEWS to 1.0))

    private fun departure(profile: WeightProfile) = DepartureQuery(setOf(PersonKey("ada@x.org")), NOW, profile)

    /** Ada and Ben commit equally; Ada does most of the reviewing. */
    private fun reviewed() = reviewedHistory(
        "core.kt",
        commits = listOf("ada@x.org" to 40L, "ben@x.org" to 39L, "ada@x.org" to 38L, "ben@x.org" to 37L),
        reviews = listOf("ada@x.org" to 30L, "ada@x.org" to 29L, "ada@x.org" to 28L, "ben@x.org" to 27L),
    )

    @Test
    fun `the breakdown shows the leaving person's share of each source`() {
        val risk = scorer.score(listOf(reviewed()), departure(bothSources)).files.single()

        assertThat(risk.sourceBreakdown).containsEntry(SourceId.COMMITS, 0.5).containsEntry(REVIEWS, 0.75)
    }

    @Test
    fun `the share of a file is the average of its sources, weighted by the profile`() {
        val evenly = scorer.score(listOf(reviewed()), departure(bothSources)).files.single()
        val reviewsCountDouble = bothSources.copy(sourceWeights = mapOf(SourceId.COMMITS to 1.0, REVIEWS to 2.0))
        val weighted = scorer.score(listOf(reviewed()), departure(reviewsCountDouble)).files.single()

        assertThat(evenly.departingShare).isEqualTo(0.625)
        assertThat(weighted.departingShare).isEqualTo((0.5 + 2 * 0.75) / 3, within(1e-12))
    }

    @Test
    fun `a source the profile does not name is left out of the ranking`() {
        val commitsOnly = scorer.score(listOf(reviewed()), departure(WeightProfile())).files.single()

        assertThat(commitsOnly.sourceBreakdown.keys).containsExactly(SourceId.COMMITS)
        assertThat(commitsOnly.departingShare).isEqualTo(0.5)
    }

    @Test
    fun `a file only a source with no weight knows is not on the map`() {
        val reviewOnly = FileHistory(REPOSITORY, "docs.md", listOf(review("ada@x.org", 10)))

        val map = scorer.score(listOf(reviewOnly), departure(WeightProfile()))

        assertThat(map.summary.files).isZero()
        assertThat(map.files).isEmpty()
    }

    @Test
    fun `a review cannot found a file`() {
        val reviewedFirst = FileHistory(
            REPOSITORY,
            "core.kt",
            listOf(review("ben@x.org", 50), touch("ada@x.org", 40), touch("ada@x.org", 30)),
        )

        val authors = Authorship(bothSources, NOW).authorsOf(reviewedFirst)

        assertThat(authors).containsExactly(PersonKey("ada@x.org"))
    }

    @Test
    fun `a source weight scales how much its touches count`() {
        val touch = review("ada@x.org", 0)

        assertThat(Authorship(bothSources, NOW).weight(touch)).isEqualTo(1.0)
        assertThat(Authorship(bothSources.copy(sourceWeights = mapOf(REVIEWS to 0.25)), NOW).weight(touch))
            .isEqualTo(0.25)
    }

    @Test
    fun `weights and names must make sense`() {
        assertThrows<IllegalArgumentException> { WeightProfile(sourceWeights = mapOf(REVIEWS to -1.0)) }
        assertThrows<IllegalArgumentException> { WeightProfile(name = " ") }
        assertThrows<IllegalArgumentException> { SourceId(" ") }
    }

    @Test
    fun `histories of several sources are joined by repository and path in time order`() {
        val commits = FileHistory(REPOSITORY, "core.kt", listOf(touch("ada@x.org", 30), touch("ada@x.org", 10)))
        val reviews = FileHistory(REPOSITORY, "core.kt", listOf(review("ben@x.org", 20)))
        val other = FileHistory("github:acme/other", "core.kt", listOf(touch("ada@x.org", 5)))

        val merged = FileHistories.merge(listOf(listOf(commits, other), listOf(reviews)))

        assertThat(merged).hasSize(2)
        val core = merged.single { it.repository == REPOSITORY }
        assertThat(core.touches.map { it.source }).containsExactly(SourceId.COMMITS, REVIEWS, SourceId.COMMITS)
        assertThat(core.touches.map { it.at }).isSorted()
    }
}
