package com.sprintstart.sprintstartbackend.offboarding.scoring

import java.time.Duration
import kotlin.math.pow

/** What a departure means for one file. */
internal sealed interface Assessment {
    /** Nobody active knew the file before the departure. */
    data object AlreadyOrphaned : Assessment

    /** The departure leaves the file with two or more authors, or none of the leaving people knew it. */
    data object Unaffected : Assessment

    /** The departure leaves the file with at most one author. */
    data class AtRisk(
        val risk: FileRisk,
    ) : Assessment
}

/** Judges files against one departure. */
internal class Assessor(
    private val query: DepartureQuery,
    histories: List<FileHistory>,
) {
    private val knowledge = Knowledge.of(query.profile, query.asOf)
    private val inactive = inactivePeople(histories)
    private val busiest = histories.maxOfOrNull(::activity) ?: 0.0

    fun assess(history: FileHistory): Assessment {
        val knowers = knowledge.authorsOf(history) - inactive
        val remaining = knowers - query.departing
        return when {
            knowers.isEmpty() -> Assessment.AlreadyOrphaned
            knowers.none { it in query.departing } -> Assessment.Unaffected
            remaining.size > 1 -> Assessment.Unaffected
            else -> Assessment.AtRisk(risk(history, remaining))
        }
    }

    private fun risk(history: FileHistory, remaining: Set<PersonKey>): FileRisk {
        val bare = bareRisk(history, remaining)
        return bare.copy(reasons = Reasons.of(bare, query.asOf))
    }

    private fun bareRisk(history: FileHistory, remaining: Set<PersonKey>): FileRisk {
        val breakdown = departingShareBySource(history)
        val share = weightedShare(breakdown)
        val activity = if (busiest > 0) activity(history) / busiest else 0.0
        return FileRisk(
            repository = history.repository,
            path = history.path,
            level = if (remaining.isEmpty()) RiskLevel.ORPHANED else RiskLevel.HOARDED,
            score = query.profile.knowledgeWeight * share + query.profile.activityWeight * activity,
            departingShare = share,
            sourceBreakdown = breakdown,
            recentActivity = activity,
            fragmentation = fragmentation(history),
            concentration = Concentration.of(knowledge.knowledgeOf(history)),
            remainingKnowers = remaining.toList(),
            lastChangedAt = history.touches.last().at,
        )
    }

    private fun departingShareBySource(history: FileHistory): Map<SourceId, Double> =
        history.touches.groupBy { it.source }.mapValues { (_, touches) ->
            touches.count { it.person in query.departing }.toDouble() / touches.size
        }

    private fun weightedShare(breakdown: Map<SourceId, Double>): Double {
        val weights = breakdown.keys.associateWith { query.profile.weightOf(it) }
        return breakdown.entries.sumOf { (source, share) -> weights.getValue(source) * share } / weights.values.sum()
    }

    private fun activity(history: FileHistory): Double = history.touches.sumOf(knowledge::weight)

    private fun fragmentation(history: FileHistory): Double {
        val shares = history.touches
            .groupingBy { it.person }
            .eachCount()
            .values
        return 1 - shares.sumOf { (it.toDouble() / history.touches.size).pow(2) }
    }

    private fun inactivePeople(histories: List<FileHistory>): Set<PersonKey> {
        val limit = query.asOf.minus(Duration.ofDays(query.profile.inactiveAfterDays))
        val lastSeen = histories.flatMap { it.touches }.groupBy({ it.person }, { it.at })
        return lastSeen.filterValues { moments -> moments.max() < limit }.keys
    }
}
