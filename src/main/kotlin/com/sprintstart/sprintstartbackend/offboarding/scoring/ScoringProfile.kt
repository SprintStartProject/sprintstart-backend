package com.sprintstart.sprintstartbackend.offboarding.scoring

/**
 * The knobs of the scoring, so one departure can be ranked under several settings and compared.
 *
 * @property halfLifeDays After this many days a change counts half as much as a change made today.
 * @property inactiveAfterDays A person with no change for this long before the date is treated as
 *           already gone.
 * @property authorThreshold A person is an author of a file when their degree of authorship reaches
 *           this fraction of the file's highest.
 * @property knowledgeWeight How much of the score comes from the leaving people's share of the file.
 * @property activityWeight How much of the score comes from how recently and often the file changes.
 */
data class ScoringProfile(
    val halfLifeDays: Double = DEFAULT_HALF_LIFE_DAYS,
    val inactiveAfterDays: Long = DEFAULT_INACTIVE_AFTER_DAYS,
    val authorThreshold: Double = DEFAULT_AUTHOR_THRESHOLD,
    val knowledgeWeight: Double = DEFAULT_KNOWLEDGE_WEIGHT,
    val activityWeight: Double = DEFAULT_ACTIVITY_WEIGHT,
) {
    init {
        require(halfLifeDays > 0) { "The half-life must be positive" }
        require(inactiveAfterDays > 0) { "The inactivity limit must be positive" }
        require(authorThreshold > 0 && authorThreshold < 1) { "The author threshold must be between 0 and 1" }
        require(knowledgeWeight >= 0 && activityWeight >= 0) { "Weights must not be negative" }
    }

    private companion object {
        const val DEFAULT_HALF_LIFE_DAYS = 152.0
        const val DEFAULT_INACTIVE_AFTER_DAYS = 365L
        const val DEFAULT_AUTHOR_THRESHOLD = 0.75
        const val DEFAULT_KNOWLEDGE_WEIGHT = 0.7
        const val DEFAULT_ACTIVITY_WEIGHT = 0.3
    }
}
