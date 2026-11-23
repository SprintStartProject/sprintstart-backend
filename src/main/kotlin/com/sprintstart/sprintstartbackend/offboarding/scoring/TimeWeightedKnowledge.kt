package com.sprintstart.sprintstartbackend.offboarding.scoring

import java.time.Duration
import java.time.Instant
import kotlin.math.pow

private const val MILLIS_PER_DAY = 86_400_000.0

/**
 * Decides who knows a file by how much weighted evidence a person has gathered, as the team's metric
 * paper proposes.
 *
 * A person's knowledge of a file is the sum of the weights of their changes, each of which halves every
 * half-life, plus a bonus for the person who created the file. Whoever reaches the configured weight
 * knows the file, so a person who touched a file once long ago does not.
 */
class TimeWeightedKnowledge(
    private val profile: WeightProfile,
    private val asOf: Instant,
) : Knowledge {
    override fun weight(touch: Touch): Double {
        val ageDays = Duration.between(touch.at, asOf).toMillis().coerceAtLeast(0) / MILLIS_PER_DAY
        return profile.weightOf(touch.source) * 0.5.pow(ageDays / profile.halfLifeDays)
    }

    /** What each person who touched [history]'s file knows of it, by weight. */
    fun knowledgeOf(history: FileHistory): Map<PersonKey, Double> {
        val founding = history.touches.firstOrNull { it.founding }
        val gathered = history.touches
            .groupBy({ it.person }, ::weight)
            .mapValues { it.value.sum() }
            .toMutableMap()
        if (founding != null) {
            gathered.merge(founding.person, profile.knowledge.foundingBonus * weight(founding), Double::plus)
        }
        return gathered
    }

    override fun authorsOf(history: FileHistory): Set<PersonKey> =
        knowledgeOf(history).filterValues { it >= profile.knowledge.knowsAtLeast }.keys
}
