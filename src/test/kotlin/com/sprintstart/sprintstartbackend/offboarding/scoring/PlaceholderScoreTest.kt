package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.util.UUID

class PercentileRankTest {
    @Test
    fun `a value is ranked by the share of values below it`() {
        val rank = PercentileRank(listOf(1.0, 2.0, 3.0, 4.0))

        assertThat(rank.of(1.0)).isCloseTo(0.125, within(1e-9))
        assertThat(rank.of(4.0)).isCloseTo(0.875, within(1e-9))
    }

    @Test
    fun `values that tie share the middle of their rank`() {
        assertThat(PercentileRank(listOf(2.0, 2.0)).of(2.0)).isCloseTo(0.5, within(1e-9))
    }

    @Test
    fun `nothing to rank against ranks nothing`() {
        assertThat(PercentileRank(emptyList()).of(5.0)).isEqualTo(0.0)
    }

    @Test
    fun `a value outside the others ranks at the ends`() {
        val rank = PercentileRank(listOf(1.0, 2.0))

        assertThat(rank.of(0.0)).isEqualTo(0.0)
        assertThat(rank.of(9.0)).isEqualTo(1.0)
    }
}

class PlaceholderScoreTest {
    @Test
    fun `a file nobody who knows it is left, all of its lines gone, one knower and fully relevant scores one`() {
        assertThat(PlaceholderScore.of(RiskLevel.ORPHANED, 1.0, 1.0, 1.0)).isCloseTo(1.0, within(1e-9))
    }

    @Test
    fun `a dormant file with one knower left and shared knowledge keeps a floor of a quarter of its risk`() {
        val score = PlaceholderScore.of(RiskLevel.HOARDED, 0.0, 2.0, 0.0)

        assertThat(score).isCloseTo((0.5 * 0.75 + 0.2 * 0.5) * 0.25, within(1e-9))
    }

    @Test
    fun `relevance raises the score and nothing is above one`() {
        val dormant = PlaceholderScore.of(RiskLevel.ORPHANED, 0.5, 1.5, 0.0)
        val busy = PlaceholderScore.of(RiskLevel.ORPHANED, 0.5, 1.5, 1.0)

        assertThat(busy).isGreaterThan(dormant * 3.9)
        assertThat(busy).isLessThanOrEqualTo(1.0)
    }

    @Test
    fun `a file nobody knows counts as fully concentrated`() {
        assertThat(PlaceholderScore.of(RiskLevel.ORPHANED, 0.0, 0.0, 0.0))
            .isCloseTo((0.5 + 0.2) * 0.25, within(1e-9))
    }

    @Test
    fun `more lines from people who left raise the score`() {
        val few = PlaceholderScore.of(RiskLevel.ORPHANED, 0.1, 1.0, 0.5)
        val many = PlaceholderScore.of(RiskLevel.ORPHANED, 0.9, 1.0, 0.5)

        assertThat(many).isGreaterThan(few)
    }
}

class RelevanceTest {
    private val commits = List(4) { UUID.randomUUID() }

    private fun file(path: String, vararg changedIn: Int) =
        FileHistory(
            REPOSITORY,
            path,
            changedIn.mapIndexed { n, c -> Touch(PersonKey("ada@x.org"), daysAgo(50L - n), commit = commits[c]) },
        )

    @Test
    fun `a busy coupled complicated file is more relevant than a calm one that stands alone`() {
        val busy = file("busy.kt", 0, 1, 2)
        val partner = file("partner.kt", 0, 1, 2)
        val calm = file("calm.kt", 3)
        val all = listOf(busy, partner, calm)
        val activity = { history: FileHistory -> history.touches.size.toDouble() }
        val hotspots = Hotspots(
            all.associate { FileKey(it.repository, it.path) to activity(it) },
            mapOf(
                FileKey(REPOSITORY, "busy.kt") to 50.0,
                FileKey(REPOSITORY, "calm.kt") to 1.0,
                FileKey(REPOSITORY, "partner.kt") to 20.0,
            ),
        )

        val relevance = Relevance(all, activity, hotspots, ChangeCoupling(all))

        assertThat(relevance.of(busy)).isGreaterThan(relevance.of(calm))
        assertThat(relevance.of(calm)).isLessThan(0.5)
        assertThat(relevance.of(busy)).isLessThanOrEqualTo(1.0)
    }

    @Test
    fun `without any hotspot only activity and coupling count`() {
        val only = file("only.kt", 0)
        val relevance = Relevance(listOf(only), { 1.0 }, Hotspots(emptyMap(), emptyMap()), ChangeCoupling(listOf(only)))

        assertThat(relevance.of(only)).isCloseTo(0.4 * 0.5 + 0.4 * 0.5 + 0.2 * 0.5, within(1e-9))
    }

    @Test
    fun `a ranking carries the placeholder score of the files at risk without changing their order`() {
        val departure = DepartureQuery(setOf(PersonKey("ada@x.org")), NOW)
        val first = file("first.kt", 0, 1, 2)
        val second = file("second.kt", 3)

        val map = RiskScorer().score(listOf(first, second), departure)

        assertThat(map.files).hasSize(2)
        assertThat(map.files.all { it.placeholderScore > 0.0 && it.placeholderScore <= 1.0 }).isTrue()
    }
}
