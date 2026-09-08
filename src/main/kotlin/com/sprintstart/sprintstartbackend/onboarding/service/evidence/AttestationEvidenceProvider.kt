package com.sprintstart.sprintstartbackend.onboarding.service.evidence

import com.sprintstart.sprintstartbackend.onboarding.external.enums.AttestationState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionState
import com.sprintstart.sprintstartbackend.onboarding.external.enums.Rigor
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Attestation
import com.sprintstart.sprintstartbackend.onboarding.repository.AttestationRepository
import com.sprintstart.sprintstartbackend.onboarding.service.Contribution
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Work a named person confirmed, as contributions.
 *
 * The source that lets a role with nothing observable — a Scrum Master, a PM, an HR hire — finish
 * onboarding at all. It reports [Rigor.ATTESTED] rather than [Rigor.OBSERVED] and that difference
 * is carried all the way to the PM readout: a colleague vouching is real evidence and is not the
 * same measurement as a merge, and blending the two would launder the difference.
 *
 * Unlike pull requests this reads a table, because an attestation exists nowhere else until
 * somebody is asked for it.
 */
@Component
class AttestationEvidenceProvider(
    private val attestationRepository: AttestationRepository,
) : EvidenceProvider {
    override val kind = ContributionEvidenceKind.ATTESTATION

    /**
     * Attribution needs no declared identity here: an attestation is filed against the hire's user
     * id, so unlike pull requests it works for somebody who has never linked a GitHub account.
     */
    override fun contributionsFor(member: ProjectMember, projectId: UUID): List<Contribution> {
        return attestationRepository
            .findAllByHireIdAndProjectId(member.userId, projectId)
            .map { it.toContribution() }
    }

    private fun Attestation.toContribution(): Contribution {
        return Contribution(
            evidenceRef = id,
            kind = ContributionEvidenceKind.ATTESTATION,
            rigor = Rigor.ATTESTED,
            state = when (state) {
                AttestationState.ACCEPTED -> ContributionState.ACCEPTED
                AttestationState.REQUESTED -> ContributionState.IN_FLIGHT
                AttestationState.WITHDRAWN -> ContributionState.ABANDONED
            },
            // Requesting is the hire's submission: the moment their work went to somebody else,
            // which is the same thing opening a pull request measures.
            openedAt = requestedAt,
            firstResponseAt = firstResponseAt,
            acceptedAt = acceptedAt,
            returnedCount = returnedCount,
        )
    }
}
