package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class MapComparisonsTest {
    private fun file(
        path: String,
        score: Double = 0.5,
        level: String = "ORPHANED",
        repository: String = "github:acme/app",
    ) = RankedFile(repository, path, level, score)

    private fun ranking(vararg paths: String) = paths.map { file(it) }

    @Test
    fun `two identical rankings agree completely`() {
        val comparison = MapComparisons.compare(ranking("a", "b", "c"), ranking("a", "b", "c"))

        assertThat(comparison.both).isEqualTo(3)
        assertThat(comparison.onlyLeft).isZero()
        assertThat(comparison.onlyRight).isZero()
        assertThat(comparison.topOverlap).isEqualTo(1.0)
        assertThat(comparison.rankCorrelation).isEqualTo(1.0)
        assertThat(comparison.movements.map { it.leftRank to it.rightRank }).containsExactly(1 to 1, 2 to 2, 3 to 3)
    }

    @Test
    fun `a reversed ranking is perfectly anti-correlated`() {
        val comparison = MapComparisons.compare(ranking("a", "b", "c"), ranking("c", "b", "a"))

        assertThat(comparison.rankCorrelation).isEqualTo(-1.0)
    }

    @Test
    fun `one swapped pair lowers the correlation without reversing it`() {
        val comparison = MapComparisons.compare(ranking("a", "b", "c", "d"), ranking("b", "a", "c", "d"))

        assertThat(comparison.rankCorrelation).isEqualTo(0.8, within(1e-12))
    }

    @Test
    fun `rankings with no file in common share nothing`() {
        val comparison = MapComparisons.compare(ranking("a", "b"), ranking("c", "d"))

        assertThat(comparison.both).isZero()
        assertThat(comparison.onlyLeft).isEqualTo(2)
        assertThat(comparison.onlyRight).isEqualTo(2)
        assertThat(comparison.topOverlap).isZero()
        assertThat(comparison.rankCorrelation).isNull()
    }

    @Test
    fun `files only one ranking lists are counted on their side`() {
        val comparison = MapComparisons.compare(ranking("a", "b", "c"), ranking("b", "d"))

        assertThat(comparison.onlyLeft).isEqualTo(2)
        assertThat(comparison.onlyRight).isEqualTo(1)
        assertThat(comparison.both).isEqualTo(1)
        assertThat(comparison.rankCorrelation).isNull()
    }

    @Test
    fun `two empty rankings are the same`() {
        val comparison = MapComparisons.compare(emptyList(), emptyList())

        assertThat(comparison.topOverlap).isEqualTo(1.0)
        assertThat(comparison.rankCorrelation).isNull()
        assertThat(comparison.movements).isEmpty()
    }

    @Test
    fun `the top overlap looks only at the requested number of files`() {
        val left = ranking("a", "b", "c")
        val right = ranking("a", "c", "b")

        assertThat(MapComparisons.compare(left, right, top = 1).topOverlap).isEqualTo(1.0)
        assertThat(MapComparisons.compare(left, right, top = 2).topOverlap).isEqualTo(1.0 / 3.0, within(1e-12))
        assertThat(MapComparisons.compare(left, right, top = 3).topOverlap).isEqualTo(1.0)
    }

    @Test
    fun `the same path in two repositories is two files`() {
        val left = listOf(file("a.kt", repository = "github:acme/one"))
        val right = listOf(file("a.kt", repository = "github:acme/two"))

        val comparison = MapComparisons.compare(left, right)

        assertThat(comparison.both).isZero()
        assertThat(comparison.movements).hasSize(2)
    }

    @Test
    fun `movements list files only one side has first and then the biggest change`() {
        val left = ranking("a", "b", "c", "d")
        val right = ranking("d", "b", "c", "e")

        val movements = MapComparisons.compare(left, right).movements

        assertThat(movements.map { it.path }).containsExactly("a", "e", "d", "b", "c")
    }

    @Test
    fun `a movement carries the rank, score and level of both sides`() {
        val left = listOf(file("a", score = 0.9, level = "ORPHANED"))
        val right = listOf(file("x"), file("a", score = 0.4, level = "HOARDED"))

        val movement = MapComparisons.compare(left, right).movements.single { it.path == "a" }

        assertThat(movement.leftRank).isEqualTo(1)
        assertThat(movement.rightRank).isEqualTo(2)
        assertThat(movement.leftScore).isEqualTo(0.9)
        assertThat(movement.rightScore).isEqualTo(0.4)
        assertThat(movement.leftLevel).isEqualTo("ORPHANED")
        assertThat(movement.rightLevel).isEqualTo("HOARDED")
    }

    @Test
    fun `a file only one side lists has nothing on the other`() {
        val movement = MapComparisons.compare(ranking("a"), emptyList()).movements.single()

        assertThat(movement.rightRank).isNull()
        assertThat(movement.rightScore).isNull()
        assertThat(movement.rightLevel).isNull()
    }
}
