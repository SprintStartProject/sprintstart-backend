package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
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

    /**
     * Records that reconciliation looked at these rows and found nothing to change.
     *
     * One statement rather than one save per row. `sourceCheckedAt` is the whole reason a pass
     * would otherwise write every row it examined: the column exists to keep "nobody has checked"
     * and "checked, and the tracker said nothing" apart, so a pass that changed nothing still has
     * to say it ran. Rows that *did* change are saved individually and are not passed here.
     *
     * A bulk update bypasses the persistence context, so the entities the caller is holding keep
     * their old `sourceCheckedAt` in memory. That is why this takes ids rather than entities —
     * nothing should read those instances afterwards expecting the new timestamp.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE StarterWorkTaskProposal p SET p.sourceCheckedAt = :now WHERE p.id IN :ids")
    fun markSourceChecked(@Param("ids") ids: Collection<UUID>, @Param("now") now: Instant): Int
}
