package com.sprintstart.sprintstartbackend.onboarding.service.evidence

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import com.sprintstart.sprintstartbackend.onboarding.service.Contribution
import com.sprintstart.sprintstartbackend.user.external.ProjectMember
import java.util.UUID

/**
 * One source of evidence that a hire completed real work.
 *
 * Every implementation answers "what has this person completed here", keys off the resolved
 * [ProjectMember] rather than a bare id, and returns the same four moments. All that differs is
 * *where it looks* and *how strong the result is*, which is why [kind] is the only thing an
 * implementation declares beyond the read itself.
 *
 * Every provider runs for every hire. No provider is gated on the kind of work somebody is
 * expected to do: what counts as evidence is not narrowed by anybody's role.
 */
interface EvidenceProvider {
    /** What this provider produces, and therefore how strong its contributions are. */
    val kind: ContributionEvidenceKind

    /**
     * Everything [member] has contributed to this project through this source.
     *
     * @param member The hire, already resolved against the project.
     * @param projectId The project to look in.
     * @return Their contributions from this source, empty when there are none or when this source
     * has nothing to attribute them by.
     */
    fun contributionsFor(member: ProjectMember, projectId: UUID): List<Contribution>
}
