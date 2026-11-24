package com.sprintstart.sprintstartbackend.offboarding.service

import com.sprintstart.sprintstartbackend.offboarding.model.request.RiskRequest
import com.sprintstart.sprintstartbackend.offboarding.scoring.DepartureQuery
import com.sprintstart.sprintstartbackend.offboarding.scoring.EvidenceSource
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileFactsReader
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileHistories
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskMap
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskScorer
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Service
import java.time.Clock

/** Answers "what do we lose if these people leave?" for a project, from the evidence ingestion stored. */
@Service
class OffboardingRiskService(
    private val sources: List<EvidenceSource>,
    private val links: IdentityLinkService,
    private val scorer: RiskScorer,
    private val facts: FileFactsReader,
    private val clock: Clock,
) {
    /**
     * Simulates a departure over a project's recorded history.
     *
     * Identities a manager confirmed as one person count as one, both in the history and among the leaving.
     *
     * @param projectId The project whose repositories are read.
     * @param request Who leaves and how to weigh the evidence; the date defaults to now.
     */
    fun riskMap(projectId: ProjectId, request: RiskRequest): RiskMap {
        val asOf = request.asOf ?: clock.instant()
        val weighed = sources.filter { request.profile.weightOf(it.id) > 0 }
        val identities = links.identities(projectId)
        val histories = FileHistories.merge(weighed.map { it.histories(projectId, asOf) }).map(identities::resolve)
        val departing = identities.resolve(request.departing)
        val fileFacts = facts.of(histories.map { it.repository }.toSet(), identities)
        return scorer.score(histories, DepartureQuery(departing, asOf, request.profile), fileFacts)
    }
}
