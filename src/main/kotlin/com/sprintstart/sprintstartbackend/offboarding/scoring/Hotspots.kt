package com.sprintstart.sprintstartbackend.offboarding.scoring

/**
 * Finds the files that are both complicated and changed often, where missing knowledge hurts most.
 *
 * The hotspot of a file is its percentile rank by activity times its percentile rank by complexity,
 * among the files of the same analysis, so it needs no threshold that would only fit one project.
 * Complexity is ranked only among files of the same kind, so a long page of documentation is a hotspot
 * among documents, not against code. A file whose complexity was never measured has no hotspot.
 *
 * @param activities How much each file was changed, with fading, by file.
 * @param complexities How much each file holds, by file.
 */
class Hotspots(
    private val activities: Map<FileKey, Double>,
    private val complexities: Map<FileKey, Complexity>,
) {
    private val activityRank = PercentileRank(activities.values)
    private val complexityRanks =
        complexities
            .filterKeys { it in activities }
            .values
            .groupBy({ it.kind }, { it.value })
            .mapValues { PercentileRank(it.value) }

    /** The hotspot of [history]: its file, from 0 to 1, or `null` when its complexity is unknown. */
    fun of(history: FileHistory): Double? {
        val key = FileKey(history.repository, history.path)
        val complexity = complexities[key] ?: return null
        val activity = activities[key] ?: return null
        return activityRank.of(activity) * complexityRanks.getValue(complexity.kind).of(complexity.value)
    }
}
