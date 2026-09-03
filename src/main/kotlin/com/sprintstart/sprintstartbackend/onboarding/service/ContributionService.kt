package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.service.evidence.EvidenceProvider
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * The real work a hire has completed on a project — whatever form that work takes.
 *
 * A [Contribution] is a unit of real work stated generally: authored by the hire, answered by
 * somebody, possibly sent back, eventually accepted or not. Those four moments are the whole
 * measurement surface and none of them are specific to git — reading pull requests directly
 * puts "this hire writes code" into the definition of progress, which leaves a Scrum Master
 * permanently at stage zero and invisible on the PM dashboard.
 *
 * Derived on read from artifacts ingestion already holds, so there is no second log to drift
 * and nothing to backfill. Attestations are the exception, and have a table.
 *
 * This service is only the composition rule: every [EvidenceProvider] runs, and their contributions
 * become one time-ordered stream that the ramp and the metrics read.
 */
@Service
class ContributionService(
    private val evidenceProviders: List<EvidenceProvider>,
) {
    /**
     * Everything [member] has contributed to this project, from every source.
     *
     * Takes the resolved [ProjectMember] rather than a user id because callers have already
     * resolved it and because identity is what a source needs: the declared GitHub login for pull
     * requests, the user id for attestations.
     *
     * Every provider runs, for every hire. Filtering the stream by the kind of work somebody is
     * expected to do would take a PM's pull requests off their own ramp.
     *
     * @param member The hire, already resolved against the project.
     * @param projectId The project to look in.
     * @return Their contributions, oldest submission first, empty when there is nothing to
     * attribute.
     */
    fun forHire(member: ProjectMember, projectId: UUID): List<Contribution> {
        return evidenceProviders
            .flatMap { it.contributionsFor(member, projectId) }
            .sortedBy { it.openedAt ?: Instant.EPOCH }
    }
}

/**
 * One unit of real work a hire produced, reduced to the moments onboarding measures.
 *
 * Not to be confused with `CompetencyKind.CONTRIBUTION`, which is a graph node kind: that is
 * the *goal* a hire is working toward, this is the *evidence* that they completed something. The
 * two meet only in that finishing the former produces the latter.
 *
 * [firstResponseAt] null means nobody has answered yet — a finding, not missing data. A
 * [returnedCount] of zero is half the operational definition of autonomy: acceptance alone cannot
 * tell clean work from work sent back three times.
 *
 * Invariant: [state] `== ACCEPTED` implies [acceptedAt] is non-null, and vice versa. It is
 * established in the mappers that build these, which are the only way one is constructed.
 */
data class Contribution(
    /** The evidence this rests on: an ingested artifact today, an attestation row later. */
    val evidenceRef: UUID,
    val kind: ContributionEvidenceKind,
    val rigor: Rigor,
    val state: ContributionState,
    val openedAt: Instant?,
    val firstResponseAt: Instant?,
    val acceptedAt: Instant?,
    val returnedCount: Int,
) {
    /** Accepted through the team's normal quality bar. The unit the ramp counts. */
    val isAccepted: Boolean
        get() = state == ContributionState.ACCEPTED

    /** Submitted and still waiting on somebody else. */
    val isInFlight: Boolean
        get() = state == ContributionState.IN_FLIGHT
}
