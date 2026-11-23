package com.sprintstart.sprintstartbackend.offboarding.scoring

/**
 * How relevant files are, measured against each other: how often they change, whether they are
 * hotspots, and how many other files they change with.
 *
 * Each of the three is ranked by percentile among the files, so none needs a threshold fitted to one
 * project, and a file that is quiet, simple and alone gets nothing.
 */
internal class Relevance(
    histories: List<FileHistory>,
    private val activity: (FileHistory) -> Double,
    private val hotspots: Hotspots,
    private val coupling: ChangeCoupling,
) {
    private val activityRank = PercentileRank(histories.map(activity))
    private val hotspotRank = PercentileRank(histories.map(::hotspot))
    private val couplingRank = PercentileRank(histories.map { coupling.of(it).sum })

    /** The relevance of [history]'s file, from 0 to 1. */
    fun of(history: FileHistory): Double =
        ACTIVITY * activityRank.of(activity(history)) +
            HOTSPOT * hotspotRank.of(hotspot(history)) +
            COUPLING * couplingRank.of(coupling.of(history).sum)

    private fun hotspot(history: FileHistory): Double = hotspots.of(history) ?: 0.0

    private companion object {
        const val ACTIVITY = 0.4
        const val HOTSPOT = 0.4
        const val COUPLING = 0.2
    }
}
