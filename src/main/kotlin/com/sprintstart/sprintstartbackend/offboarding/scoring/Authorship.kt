package com.sprintstart.sprintstartbackend.offboarding.scoring

import java.time.Duration
import java.time.Instant
import kotlin.math.ln
import kotlin.math.pow

private const val INTERCEPT = 3.293
private const val CREATION_BONUS = 1.098
private const val OWN_CHANGE = 0.164
private const val OTHERS_CHANGE = 0.321
private const val MILLIS_PER_DAY = 86_400_000.0

/**
 * Decides who counts as an author of a file, with the degree-of-authorship model from the truck-factor
 * literature.
 *
 * The degree is `3.293 + 1.098·created + 0.164·own − 0.321·ln(1 + others)`, with the counts of changes
 * replaced by recency-weighted sums so old changes fade. Whoever reaches the configured fraction of the
 * file's highest degree is an author, provided their degree is at least the intercept or they created
 * the file. This is the simplified baseline: the reference implementation adds special cases for files
 * that are re-added.
 */
class Authorship(
    private val profile: ScoringProfile,
    private val asOf: Instant,
) {
    /** How much one change still counts: 1 for a change made now, halving every half-life. */
    fun weight(touch: Touch): Double {
        val ageDays = Duration.between(touch.at, asOf).toMillis().coerceAtLeast(0) / MILLIS_PER_DAY
        return 0.5.pow(ageDays / profile.halfLifeDays)
    }

    /** The people who count as authors of [history]'s file. */
    fun authorsOf(history: FileHistory): Set<PersonKey> {
        val own = history.touches.groupBy({ it.person }, { weight(it) }).mapValues { it.value.sum() }
        val total = own.values.sum()
        val creator = history.touches.first().person
        val degrees = own.mapValues { (person, mine) -> degree(person == creator, mine, total - mine) }
        val best = degrees.values.max()
        return degrees.filter { (person, degree) -> isAuthor(degree, best, person == creator) }.keys
    }

    private fun degree(created: Boolean, own: Double, others: Double): Double =
        INTERCEPT + (if (created) CREATION_BONUS else 0.0) + OWN_CHANGE * own - OTHERS_CHANGE * ln(1 + others)

    private fun isAuthor(degree: Double, best: Double, created: Boolean): Boolean =
        best > 0 && degree / best > profile.authorThreshold && (degree >= INTERCEPT || created)
}
