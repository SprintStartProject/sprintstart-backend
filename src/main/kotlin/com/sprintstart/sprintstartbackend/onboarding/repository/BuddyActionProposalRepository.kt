package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyActionProposal
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

interface BuddyActionProposalRepository : JpaRepository<BuddyActionProposal, UUID> {
    /**
     * Moves a proposal from [from] to [to] only if it is still in [from], in one statement.
     *
     * The single-use guarantee rests on this: two confirms that both read `PROPOSED` both issue this
     * update, the database applies them one after the other, and only the first still matches. The
     * version is bumped too, so an entity loaded before the transition cannot overwrite it.
     *
     * @return 1 when this call made the transition, 0 when the proposal had already moved.
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        """UPDATE BuddyActionProposal p
           SET p.status = :to, p.decidedAt = :at, p.version = p.version + 1
           WHERE p.id = :id AND p.status = :from""",
    )
    fun transition(id: UUID, from: BuddyProposalStatus, to: BuddyProposalStatus, at: Instant): Int

    /**
     * Records how a claimed proposal ended: moves it out of [from] to [to] with the line shown to the
     * manager, only if it is still in [from].
     *
     * One statement rather than load-and-save, so recording an outcome cannot lose an optimistic-lock
     * race after the action itself has already committed.
     *
     * @return 1 when recorded, 0 when the proposal was no longer in [from].
     */
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query(
        """UPDATE BuddyActionProposal p
           SET p.status = :to, p.decidedAt = :at, p.resultMessage = :message, p.version = p.version + 1
           WHERE p.id = :id AND p.status = :from""",
    )
    fun finish(id: UUID, from: BuddyProposalStatus, to: BuddyProposalStatus, at: Instant, message: String): Int

    fun deleteAllByUserId(userId: UUID)
}
