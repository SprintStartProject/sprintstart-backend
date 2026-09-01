package com.sprintstart.sprintstartbackend.onboarding.service.evidence

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.AssignedIssue
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.service.Contribution
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Issues assigned to the hire in a connected tracker, as contributions.
 *
 * An assigned tracker issue somebody else accepted produces [Rigor.OBSERVED] evidence, on the same
 * stream and measured by the same four moments as a merged pull request — so a role that never
 * opens a pull request needs nobody to vouch for it.
 *
 * Mounted unconditionally, never gated on anybody's role (see [EvidenceProvider]): a developer
 * who also closes tickets gets credit for both.
 *
 * Nothing here is stored. Derived on read from ingested issues, exactly as pull requests
 * are, so there is no backfill and no second copy to drift.
 */
@Component
class TrackedIssueEvidenceProvider(
    private val artifactIngestionApi: ArtifactIngestionApi,
) : EvidenceProvider {
    override val kind = ContributionEvidenceKind.TRACKED_ISSUE

    /**
     * A member who has declared no tracker name yields nothing rather than an error — the same
     * shape as a blank GitHub login, and read the same way: no attribution is possible, which is a
     * different fact from having done no work.
     */
    override fun contributionsFor(member: ProjectMember, projectId: UUID): List<Contribution> {
        val assignee = member.jiraDisplayName
        if (assignee.isNullOrBlank()) {
            return emptyList()
        }
        return artifactIngestionApi
            .getAssignedIssues(projectId, assignee)
            .map { it.toContribution() }
    }

    /**
     * There is no abandoned state, on purpose. Jira files "Won't Do" under the same *Done*
     * category as "Done" and the connector does not parse the resolution field, so an issue that
     * was dropped and one that was finished are indistinguishable here. An unaccepted issue is
     * therefore in flight — which is the honest reading, and never counts as accepted work either
     * way. Guessing from status *names* would mean guessing at whatever each team typed.
     */
    private fun AssignedIssue.toContribution(): Contribution =
        Contribution(
            evidenceRef = artifactId,
            kind = ContributionEvidenceKind.TRACKED_ISSUE,
            // OBSERVED, not ATTESTED: nobody was asked, and the acceptance the reader accepts is
            // one somebody *other than the hire* performed -- see AssignedIssueReader.
            rigor = Rigor.OBSERVED,
            state = if (acceptedAt != null) ContributionState.ACCEPTED else ContributionState.IN_FLIGHT,
            openedAt = openedAt,
            firstResponseAt = firstResponseAt,
            acceptedAt = acceptedAt,
            returnedCount = returnedCount,
        )
}
