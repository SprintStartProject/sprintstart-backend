package com.sprintstart.sprintstartbackend.onboarding.service.evidence

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.ingestion.external.AuthoredPullRequest
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.service.Contribution
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Pull requests the hire authored, as contributions.
 *
 * The original and strongest source: ingestion observed all four moments, so nothing here depends
 * on anybody's account of what happened. Derived on read, never stored — these facts already live
 * in ingested artifacts and a second copy would drift.
 */
@Component
class PullRequestEvidenceProvider(
    private val artifactIngestionApi: ArtifactIngestionApi,
) : EvidenceProvider {
    override val kind = ContributionEvidenceKind.PULL_REQUEST

    /**
     * A blank GitHub login yields nothing rather than an error: no declared identity means no
     * attribution is possible, which callers already distinguish from "did no work".
     */
    override fun contributionsFor(member: ProjectMember, projectId: UUID): List<Contribution> {
        val login = member.githubLogin
        if (login.isNullOrBlank()) {
            return emptyList()
        }
        return artifactIngestionApi
            .getAuthoredPullRequests(projectId, login)
            .map { it.toContribution() }
    }

    private fun AuthoredPullRequest.toContribution(): Contribution {
        return Contribution(
            evidenceRef = artifactId,
            kind = ContributionEvidenceKind.PULL_REQUEST,
            rigor = Rigor.OBSERVED,
            state = stateOf(this),
            openedAt = openedAt,
            firstResponseAt = firstResponseAt,
            acceptedAt = mergedAt,
            returnedCount = changesRequestedCount,
        )
    }

    /**
     * A merged pull request is accepted; a genuinely open one is in flight; anything else was
     * closed without merging.
     *
     * Leans on [AuthoredPullRequest.isOpen] rather than re-deriving openness, so "open" keeps
     * meaning what it means everywhere else — merge state alone would count a closed-unmerged pull
     * request as still waiting.
     */
    private fun stateOf(pullRequest: AuthoredPullRequest): ContributionState {
        return when {
            pullRequest.mergedAt != null -> ContributionState.ACCEPTED
            pullRequest.isOpen -> ContributionState.IN_FLIGHT
            else -> ContributionState.ABANDONED
        }
    }
}
