package com.sprintstart.sprintstartbackend.offboarding.scoring

/**
 * The placeholder score of the team's metric selection: how much knowledge is at risk, scaled by how
 * relevant the file is.
 *
 * The knowledge risk is half the level (nobody left 1, one person left 0.75), 0.3 of the share of
 * lines written by people who left, and 0.2 of one over the effective number of knowers. It is
 * multiplied by a floor of 0.25 plus 0.75 of the relevance, so a dormant file stays visible but a
 * relevant one rises. The weights are provisional, as in the selection, and meant to be tuned.
 */
object PlaceholderScore {
    private const val LEVEL_WEIGHT = 0.5
    private const val ABANDONED_WEIGHT = 0.3
    private const val CONCENTRATION_WEIGHT = 0.2
    private const val ORPHANED = 1.0
    private const val HOARDED = 0.75
    private const val FLOOR = 0.25
    private const val RELEVANCE = 0.75

    /**
     * The score of one file at risk, from 0 to 1.
     *
     * @param level How the departure leaves the file.
     * @param abandonedShare The share of its lines written by people who left; 0 when it was never blamed.
     * @param effectiveKnowers How many equally strong knowers its knowledge is worth; 0 when nobody knows it.
     * @param relevance How relevant the file is, from 0 to 1.
     */
    fun of(level: RiskLevel, abandonedShare: Double, effectiveKnowers: Double, relevance: Double): Double {
        val concentrated = if (effectiveKnowers > 0) 1 / effectiveKnowers else 1.0
        val atRisk = LEVEL_WEIGHT * (if (level == RiskLevel.ORPHANED) ORPHANED else HOARDED) +
            ABANDONED_WEIGHT * abandonedShare +
            CONCENTRATION_WEIGHT * concentrated
        return atRisk * (FLOOR + RELEVANCE * relevance)
    }
}
