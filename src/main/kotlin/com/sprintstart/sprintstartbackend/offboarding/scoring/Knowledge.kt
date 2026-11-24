package com.sprintstart.sprintstartbackend.offboarding.scoring

import java.time.Instant

/** How to decide who knows a file. */
enum class KnowledgeModel {
    /** Whoever reaches a fraction of the highest degree of authorship, from the truck-factor literature. */
    AUTHORSHIP,

    /** Whoever has gathered enough weighted, fading evidence: the founding change, commits and reviews. */
    TIME_WEIGHTED,
}

/**
 * Chooses and tunes the way knowledge of a file is decided.
 *
 * @property model Which way to decide who knows a file.
 * @property knowsAtLeast With [KnowledgeModel.TIME_WEIGHTED], the weight of evidence a person needs to know
 *           a file; 1 is about one commit made today.
 * @property foundingBonus With [KnowledgeModel.TIME_WEIGHTED], how many extra commits' weight the person
 *           who created the file gets.
 */
data class KnowledgeSettings(
    val model: KnowledgeModel = KnowledgeModel.AUTHORSHIP,
    val knowsAtLeast: Double = DEFAULT_KNOWS_AT_LEAST,
    val foundingBonus: Double = DEFAULT_FOUNDING_BONUS,
) {
    init {
        require(knowsAtLeast > 0) { "The weight needed to know a file must be positive" }
        require(foundingBonus >= 0) { "The founding bonus must not be negative" }
    }

    private companion object {
        const val DEFAULT_KNOWS_AT_LEAST = 1.0
        const val DEFAULT_FOUNDING_BONUS = 3.0
    }
}

/** Decides who knows a file, and how much each change still counts. */
interface Knowledge {
    /** How much one touch still counts as of the date. */
    fun weight(touch: Touch): Double

    /** What each person who touched [history]'s file knows of it, by weight. */
    fun knowledgeOf(history: FileHistory): Map<PersonKey, Double>

    /** The people who know [history]'s file. */
    fun authorsOf(history: FileHistory): Set<PersonKey>

    companion object {
        /** The knowledge model [profile] asks for, as of [asOf]. */
        fun of(profile: WeightProfile, asOf: Instant): Knowledge = when (profile.knowledge.model) {
            KnowledgeModel.AUTHORSHIP -> Authorship(profile, asOf)
            KnowledgeModel.TIME_WEIGHTED -> TimeWeightedKnowledge(profile, asOf)
        }
    }
}
