package com.sprintstart.sprintstartbackend.arrival.service

import com.sprintstart.sprintstartbackend.arrival.external.ContributionApi
import com.sprintstart.sprintstartbackend.arrival.external.model.Contribution
import com.sprintstart.sprintstartbackend.arrival.service.evidence.EvidenceProvider
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
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
 * become one time-ordered stream that the metrics read.
 */
@Service
class ContributionService(
    private val evidenceProviders: List<EvidenceProvider>,
) : ContributionApi {
    /**
     * Everything [member] has contributed to this project, from every source.
     *
     * Takes the resolved [ProjectMember] rather than a user id because callers have already
     * resolved it and because identity is what a source needs: the declared GitHub login for pull
     * requests, the user id for attestations.
     *
     * Every provider runs, for every hire. Filtering the stream by the kind of work somebody is
     * expected to do would take a PM's pull requests off their own timeline.
     *
     * @param member The hire, already resolved against the project.
     * @param projectId The project to look in.
     * @return Their contributions, oldest submission first, empty when there is nothing to
     * attribute.
     */
    override fun contributionsOf(member: ProjectMember, projectId: ProjectId): List<Contribution> = forHire(
        member,
        projectId.value,
    )

    fun forHire(member: ProjectMember, projectId: UUID): List<Contribution> {
        return evidenceProviders
            .flatMap { it.contributionsFor(member, projectId) }
            .sortedBy { it.openedAt ?: Instant.EPOCH }
    }
}
