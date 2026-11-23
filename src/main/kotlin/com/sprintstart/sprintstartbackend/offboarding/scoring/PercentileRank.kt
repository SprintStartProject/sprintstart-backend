package com.sprintstart.sprintstartbackend.offboarding.scoring

/**
 * Ranks a value among a set of values by percentile, from 0 to 1.
 *
 * Values that tie share the middle of their rank, so the rank needs no threshold that would only fit
 * one project and is not thrown off by one outlier.
 */
class PercentileRank(
    values: Collection<Double>,
) {
    private val sorted = values.sorted()

    /** The rank of [value]: the share of the values below it, with half of those equal to it. */
    fun of(value: Double): Double {
        if (sorted.isEmpty()) return 0.0
        val below = sorted.count { it < value }
        val equal = sorted.count { it == value }
        return (below + equal / 2.0) / sorted.size
    }
}
