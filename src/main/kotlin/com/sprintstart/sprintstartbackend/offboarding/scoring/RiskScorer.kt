package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.springframework.stereotype.Component

/**
 * Simulates a departure over the recorded file histories.
 *
 * The result depends only on the histories and the query, so the same departure on the same date
 * always ranks the same way.
 */
@Component
class RiskScorer {
    /**
     * Ranks the files that lose the people who know them when [DepartureQuery.departing] leave.
     *
     * Files that nobody active knew before, and files that keep two or more authors, are only counted.
     */
    fun score(histories: List<FileHistory>, query: DepartureQuery): RiskMap {
        val visible = histories.mapNotNull { it.knownAt(query.asOf) }
        val assessor = Assessor(query, visible)
        val assessments = visible.map(assessor::assess)
        val atRisk = assessments.filterIsInstance<Assessment.AtRisk>().map { it.risk }
        return RiskMap(
            asOf = query.asOf,
            profile = query.profile,
            departing = query.departing,
            unknownDeparting = query.departing - visible.flatMap { it.touches }.map { it.person }.toSet(),
            summary = RiskSummary(
                files = visible.size,
                orphaned = atRisk.count { it.level == RiskLevel.ORPHANED },
                hoarded = atRisk.count { it.level == RiskLevel.HOARDED },
                alreadyOrphaned = assessments.count { it == Assessment.AlreadyOrphaned },
            ),
            files = atRisk.sortedWith(MOST_URGENT_FIRST),
        )
    }

    private companion object {
        val MOST_URGENT_FIRST = compareBy<FileRisk> { it.level }
            .thenByDescending { it.score }
            .thenBy { it.repository }
            .thenBy { it.path }
    }
}
