package com.sprintstart.sprintstartbackend.offboarding.scoring

/**
 * How unevenly the knowledge of a file is spread over people.
 *
 * The effective number of knowers is the inverse of the Herfindahl index of the knowledge shares, so two
 * people with equal shares are 2.0 knowers and a split of 95 % to 5 % is about 1.1. It is the reciprocal
 * of one minus the fractal value of the literature, which is 0 for one person and approaches 1 for many.
 *
 * @property effectiveKnowers How many equally strong knowers the spread is worth; 0 when nobody knows the file.
 * @property island The person who holds at least [ISLAND_SHARE] of the knowledge, or `null` when nobody does.
 */
data class Concentration(
    val effectiveKnowers: Double,
    val island: PersonKey?,
) {
    companion object {
        /** The share of the knowledge that makes one person a knowledge island. */
        const val ISLAND_SHARE = 0.95

        /** The concentration of [knowledge], what each person is worth. */
        fun of(knowledge: Map<PersonKey, Double>): Concentration {
            val total = knowledge.values.sum()
            if (total <= 0) return Concentration(0.0, null)
            val shares = knowledge.mapValues { it.value / total }
            val top = shares.maxBy { it.value }
            return Concentration(
                effectiveKnowers = 1 / shares.values.sumOf { it * it },
                island = top.key.takeIf { top.value >= ISLAND_SHARE },
            )
        }
    }
}
