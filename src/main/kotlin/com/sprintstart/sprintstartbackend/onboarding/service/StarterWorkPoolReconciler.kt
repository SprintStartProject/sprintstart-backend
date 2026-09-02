package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.ingestion.external.ArtifactIngestionApi
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.StarterWorkTaskProposal
import com.sprintstart.sprintstartbackend.onboarding.repository.StarterWorkTaskProposalRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Brings the starter-work pool back in line with what the trackers now say.
 *
 * The pool is checked against its source exactly once today, on the way in
 * ([StarterWorkTaskProposalService.promoteCandidate] refuses a closed issue). After that a row is
 * never looked at again, so an issue the team closed yesterday keeps being offered to newcomers,
 * and one somebody picked up keeps ranking as though it were free. This closes that gap.
 *
 * It reads only the ingested corpus — `ArtifactIngestionApi` serves what the last ingestion run
 * captured — so a pass costs no tracker calls and can run as often as ingestion does.
 *
 * Three rules hold the whole thing together:
 *
 * 1. **Only a definite `CLOSED` closes anything.** An issue the corpus has no state for is
 *    *unknown*, never finished — the same reading intake applies, and the reason a tracker this
 *    system ingests only partially cannot silently empty the pool.
 * 2. **[ProposalStatus.REJECTED] is never touched.** Rejection is a person's decision and sticky by
 *    design. A pass that could revive one would undo somebody's refusal every time it ran.
 * 3. **Nothing is deleted.** A stale row stays, so the pool can say *why* an issue is not on offer,
 *    and so reopening it is a status change rather than a re-mining.
 */
@Component
class StarterWorkPoolReconciler(
    private val starterWorkTaskProposalRepository: StarterWorkTaskProposalRepository,
    private val artifactIngestionApi: ArtifactIngestionApi,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * What one pass changed. Returned rather than only logged so a caller — a test, or an admin
     * endpoint later — can assert on it.
     */
    data class Outcome(
        val examined: Int,
        val markedStale: Int,
        val revived: Int,
        val assigneeChanged: Int,
        /**
         * Rows whose issue the corpus no longer holds, so nothing could be compared.
         *
         * Counted separately because it is the one number here that is a problem rather than a
         * result: a pool row whose source has vanished stays claimable forever on the strength of
         * whatever was true when it was mined.
         */
        val skipped: Int = 0,
    )

    /**
     * Compares every reconcilable row against its source and writes back what changed.
     *
     * [ProposalStatus.LIVE] and [ProposalStatus.STALE] are both examined: the first so it can go
     * stale, the second so it can come back. Rejected rows are not even loaded.
     */
    @Transactional
    fun reconcile(): Outcome {
        val rows = starterWorkTaskProposalRepository
            .findAllByStatusIn(listOf(ProposalStatus.LIVE, ProposalStatus.STALE))
        if (rows.isEmpty()) return Outcome(0, 0, 0, 0)

        var markedStale = 0
        var revived = 0
        var assigneeChanged = 0
        var skipped = 0
        val now = Instant.now()

        rows.forEach { proposal ->
            val issue = artifactIngestionApi.getIssue(proposal.sourceId)
            if (issue == null) {
                // The corpus no longer holds the issue — a source disconnected, or an artifact
                // pruned. That says nothing about whether the work is still open, so the row is
                // left exactly as it is rather than guessed at.
                skipped++
                return@forEach
            }

            val applied = apply(proposal, issue.state, issue.hasAssignee, now)
            when (applied.transition) {
                Transition.TO_STALE -> markedStale++
                Transition.TO_LIVE -> revived++
                Transition.NONE -> Unit
            }
            // Counted separately rather than as an else-branch: a row can both go stale and change
            // hands in the same pass, and collapsing the two would under-report the quieter one.
            if (applied.assigneeChanged) assigneeChanged++
        }

        val outcome = Outcome(rows.size, markedStale, revived, assigneeChanged, skipped)
        if (markedStale > 0 || revived > 0 || assigneeChanged > 0) {
            logger.info(
                "Starter-work reconciliation examined {} rows: {} went stale, {} came back, {} changed assignee",
                outcome.examined,
                outcome.markedStale,
                outcome.revived,
                outcome.assigneeChanged,
            )
        }
        if (skipped > 0) {
            // Warned rather than folded into the line above: these rows were not reconciled at all,
            // and a pool quietly serving work nobody can look up is worth somebody's attention.
            logger.warn(
                "Starter-work reconciliation could not find {} of {} pooled issues in the corpus",
                skipped,
                rows.size,
            )
        }
        return outcome
    }

    /**
     * Brings one row in line with its source, if the corpus still holds it.
     *
     * The point of reconciling a single row is that a full pass, however often it runs, is still a
     * clock — and there are moments where being out of date costs something specific. Claiming a
     * task is the obvious one: a hire committing to an issue that closed an hour ago is the exact
     * failure this whole change exists to prevent, and one local read is a cheap price at that
     * moment.
     *
     * @return true when the row's status changed.
     */
    @Transactional
    fun reconcileOne(proposal: StarterWorkTaskProposal): Boolean {
        if (proposal.status == ProposalStatus.REJECTED) return false
        val issue = artifactIngestionApi.getIssue(proposal.sourceId) ?: return false
        return apply(proposal, issue.state, issue.hasAssignee, Instant.now()).transition != Transition.NONE
    }

    /** What one row's reconciliation did: the status move, if any, and whether it changed hands. */
    private data class Applied(
        val transition: Transition,
        val assigneeChanged: Boolean,
    )

    /**
     * Writes one row's source facts back onto it and says what that changed.
     *
     * Shared by the full pass and the single-row path so the two can never drift into disagreeing
     * about what "closed" means — the asymmetry in [transitionFor] is subtle enough that a second
     * copy of it would eventually be a different rule.
     */
    private fun apply(
        proposal: StarterWorkTaskProposal,
        state: String?,
        hasAssignee: Boolean?,
        now: Instant,
    ): Applied {
        val assigneeChanged = applyAssignee(proposal, hasAssignee)
        // Read before the status is touched, or the transition would be decided against the answer
        // it is about to produce.
        val transition = transitionFor(proposal.status, state)
        when (transition) {
            Transition.TO_STALE -> proposal.status = ProposalStatus.STALE
            Transition.TO_LIVE -> proposal.status = ProposalStatus.LIVE
            Transition.NONE -> Unit
        }
        proposal.sourceCheckedAt = now
        starterWorkTaskProposalRepository.save(proposal)
        return Applied(transition, assigneeChanged)
    }

    /**
     * Records what the tracker says about an assignee, returning whether that changed anything.
     *
     * A null from the corpus is *unknown* and never overwrites a definite answer already on the
     * row: losing "somebody has this" because one pass could not tell would quietly promote a
     * taken task back up the ranking.
     */
    private fun applyAssignee(proposal: StarterWorkTaskProposal, hasAssignee: Boolean?): Boolean {
        if (hasAssignee == null || hasAssignee == proposal.sourceHasAssignee) return false
        proposal.sourceHasAssignee = hasAssignee
        return true
    }

    private enum class Transition { TO_STALE, TO_LIVE, NONE }

    /**
     * The status change one row's source state calls for, if any.
     *
     * Written as a decision on its own so the asymmetry is visible: going stale needs a *definite*
     * `CLOSED`, and coming back needs a *definite* non-closed. An unknown state moves nothing in
     * either direction, which is what keeps a partially-ingested tracker from emptying the pool on
     * one pass and refilling it on the next.
     */
    private fun transitionFor(status: ProposalStatus, state: String?): Transition {
        if (state == null) return Transition.NONE
        val closed = CLOSED_STATE.equals(state, ignoreCase = true)
        return when {
            status == ProposalStatus.LIVE && closed -> Transition.TO_STALE
            status == ProposalStatus.STALE && !closed -> Transition.TO_LIVE
            else -> Transition.NONE
        }
    }

    private companion object {
        /** How the ingestion mappers spell "finished at the source", for GitHub and Jira alike. */
        const val CLOSED_STATE = "CLOSED"
    }
}
