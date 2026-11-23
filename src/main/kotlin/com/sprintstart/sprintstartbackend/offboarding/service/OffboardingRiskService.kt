package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.scoring.DepartureQuery
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileHistories
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskMap
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskScorer
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service
import java.time.Clock

/** Answers "what do we lose if these people leave?" for a project, from the evidence ingestion stored. */
@Service
class OffboardingRiskService(
    private val evidence: ContributionEvidenceApi,
    private val scorer: RiskScorer,
    private val clock: Clock,
) {
    /**
     * Simulates a departure over a project's recorded history.
     *
     * @param projectId The project whose repositories are read.
     * @param request Who leaves and how to weigh the evidence; the date defaults to now.
     */
    fun riskMap(projectId: ProjectId, request: RiskRequest): RiskMap {
        val asOf = request.asOf ?: clock.instant()
        val histories = FileHistories.build(evidence.fileContributions(projectId, asOf))
        return scorer.score(histories, DepartureQuery(request.departing, asOf, request.profile))
    }
}
