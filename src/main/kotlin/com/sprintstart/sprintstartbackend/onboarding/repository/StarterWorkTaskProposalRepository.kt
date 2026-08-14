package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface StarterWorkTaskProposalRepository : JpaRepository<StarterWorkTaskProposal, UUID> {
    /** The live pool nobody has vouched for yet -- what a PM's review surface shows. */
    fun findAllByStatusAndReviewedFalse(status: ProposalStatus): List<StarterWorkTaskProposal>

    fun findAllByStatus(status: ProposalStatus): List<StarterWorkTaskProposal>

    fun findAllByStatusIn(statuses: Collection<ProposalStatus>): List<StarterWorkTaskProposal>

    /**
     * The one proposal an issue already has, live or rejected.
     *
     * `sourceId` is unique per proposal, so this is the authoritative answer to "is this issue
     * already in the pool, or was it taken out?" — the check that keeps promotion from duplicating a
     * mined task or quietly undoing a rejection.
     */
    fun findBySourceId(sourceId: String): StarterWorkTaskProposal?

    /** The Task-0-eligible pool: approved tasks a PM flagged as suitable for a hire's first task. */
    fun findAllByStatusAndTaskZeroEligibleTrue(status: ProposalStatus): List<StarterWorkTaskProposal>
}
