package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalStatus
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyActionProposal
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyActionResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyActionProposalRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.time.Duration
import java.util.UUID

/**
 * Stores the changes the team-mode buddy offers a manager, and runs one only when they confirm it.
 *
 * Proposing never changes anything but this table. Confirming runs, in order:
 *
 * 1. **Owner.** Only the manager the proposal was made for can decide it; anybody else gets a 404, so
 *    a proposal id says nothing about whether it exists.
 * 2. **Still open.** Anything already decided or expired is refused with a sentence, not an error.
 * 3. **Claim.** One conditional update from `PROPOSED` to `CONFIRMING`; a confirm that loses the race
 *    runs nothing.
 * 4. **Still the manager**, through [UserApi.canManageProject] — they may have lost the project since.
 * 5. **Still valid**, through the action's [TeamActionHandler.recheck].
 * 6. **Perform**, then record `CONFIRMED` or `FAILED` with the line shown to the manager.
 *
 * The outcome is recorded with a second conditional update, from `CONFIRMING`, even when the action
 * throws something unexpected — so a claimed proposal ends in `CONFIRMED` or `FAILED`. The one way it
 * can stay `CONFIRMING` is the database refusing that write; then the failure is logged, and the
 * manager is still told what actually happened rather than shown an error for a change that was made.
 */
@Service
class BuddyProposalService(
    private val buddyActionProposalRepository: BuddyActionProposalRepository,
    private val userApi: UserApi,
    private val handlersProvider: ObjectProvider<TeamActionHandler>,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    // Resolved lazily, for the same reason as BuddyTeamTools' area tools.
    private val handlers: Map<String, TeamActionHandler> by lazy {
        handlersProvider.orderedStream().toList().associateBy { it.spec.name }
    }

    /** The areas that have at least one action, so `open_area` can offer them. */
    fun actionAreas(): Set<TeamArea> = handlers.values.map { it.area }.toSet()

    /** The action tools of the areas opened so far this turn. */
    fun actionSpecs(openedAreas: Set<TeamArea>): List<BuddyToolSpecDto> =
        handlers.values.filter { it.area in openedAreas }.map { it.spec }

    fun isAction(toolName: String): Boolean = toolName in handlers

    /**
     * Turns an action call into a stored proposal, or into the reason it cannot be offered.
     *
     * @return What to tell the model, and the stored proposal when there is one to show.
     */
    fun propose(call: BuddyToolCallDto, context: TeamToolContext): ProposeOutcome {
        val handler = handlers[call.name] ?: return ProposeOutcome("Unknown action: ${call.name}.", null)
        return when (val draft = handler.draft(call, context)) {
            is TeamActionDraft.Refused -> ProposeOutcome(draft.reason, null)
            is TeamActionDraft.Proposed -> {
                val now = clock.instant()
                val proposal = buddyActionProposalRepository.save(
                    BuddyActionProposal(
                        userId = context.userId,
                        projectId = context.projectId,
                        action = handler.spec.name,
                        params = json.encodeToString(JsonObject.serializer(), draft.params),
                        label = draft.label,
                        preview = draft.preview,
                        risk = handler.risk,
                        createdAt = now,
                        expiresAt = now.plus(TIME_TO_LIVE),
                    ),
                )
                ProposeOutcome(
                    toolResult = "Proposed to the manager: “${draft.label}”. They see a confirm button with this " +
                        "preview, and nothing changes unless they confirm it. Offer it — never say it is done.",
                    proposal = proposal,
                )
            }
        }
    }

    /**
     * Runs a proposal the manager confirmed, if it may still run.
     *
     * @return The line to show; a refusal or a handled failure is `ok = false`, not an error.
     * @throws ResponseStatusException 404 when the caller does not exist or the proposal is not theirs.
     */
    suspend fun confirm(authId: String, proposalId: UUID): BuddyActionResponse {
        val proposal = ownProposal(authId, proposalId)
        if (proposal.status != BuddyProposalStatus.PROPOSED) {
            return BuddyActionResponse(ok = false, message = alreadyDecided(proposal.status))
        }
        val now = clock.instant()
        if (now.isAfter(proposal.expiresAt)) {
            buddyActionProposalRepository.transition(
                proposal.id,
                BuddyProposalStatus.PROPOSED,
                BuddyProposalStatus.EXPIRED,
                now,
            )
            return BuddyActionResponse(ok = false, message = EXPIRED_MESSAGE)
        }
        val claimed = buddyActionProposalRepository.transition(
            proposal.id,
            BuddyProposalStatus.PROPOSED,
            BuddyProposalStatus.CONFIRMING,
            now,
        )
        if (claimed == 0) {
            return BuddyActionResponse(ok = false, message = "This was already confirmed or dismissed.")
        }
        return runClaimed(proposal, authId)
    }

    /**
     * Declines a proposal. Nothing changes but the proposal itself.
     *
     * @throws ResponseStatusException 404 when the caller does not exist or the proposal is not theirs.
     */
    fun dismiss(authId: String, proposalId: UUID): BuddyActionResponse {
        val proposal = ownProposal(authId, proposalId)
        val dismissed = buddyActionProposalRepository.transition(
            proposal.id,
            BuddyProposalStatus.PROPOSED,
            BuddyProposalStatus.DISMISSED,
            clock.instant(),
        )
        return if (dismissed == 1) {
            BuddyActionResponse(ok = true, message = "Dismissed — nothing changed.")
        } else {
            BuddyActionResponse(ok = false, message = alreadyDecided(proposal.status))
        }
    }

    private suspend fun runClaimed(proposal: BuddyActionProposal, authId: String): BuddyActionResponse {
        var outcome = Outcome(ok = false, message = "Something went wrong, so this was not done.")
        try {
            outcome = attempt(proposal, authId)
        } catch (e: ResponseStatusException) {
            outcome = Outcome(ok = false, message = e.reason ?: "That did not go through.")
        } finally {
            // Also reached when the action throws something unexpected: the proposal is recorded as
            // failed rather than left claimed, and the exception still propagates.
            recordOutcome(proposal.id, outcome)
        }
        return BuddyActionResponse(ok = outcome.ok, message = outcome.message)
    }

    private suspend fun attempt(proposal: BuddyActionProposal, authId: String): Outcome {
        if (!userApi.canManageProject(authId, proposal.projectId)) {
            return Outcome(ok = false, message = "You no longer manage this project, so this was not done.")
        }
        val handler = handlers[proposal.action]
            ?: return Outcome(ok = false, message = "This action is no longer available, so nothing was done.")
        val params = json.parseToJsonElement(proposal.params).jsonObject
        val context = TeamToolContext(userId = proposal.userId, authId = authId, projectId = proposal.projectId)
        handler.recheck(params, context)?.let { return Outcome(ok = false, message = it) }
        return Outcome(ok = true, message = handler.perform(params, context))
    }

    /**
     * Records how a claimed proposal ended, without ever replacing the real result with an error.
     *
     * By the time this runs the action may have committed its change. A failure to *record* that must
     * not surface as a failed request — the manager would be told a change failed that in fact happened —
     * so it is logged, and the caller returns the real outcome.
     */
    private fun recordOutcome(proposalId: UUID, outcome: Outcome) {
        val recorded = try {
            buddyActionProposalRepository.finish(
                proposalId,
                BuddyProposalStatus.CONFIRMING,
                if (outcome.ok) BuddyProposalStatus.CONFIRMED else BuddyProposalStatus.FAILED,
                clock.instant(),
                outcome.message,
            )
        } catch (e: DataAccessException) {
            logger.error("Could not record the outcome of proposal {}; it stays CONFIRMING", proposalId, e)
            return
        }
        if (recorded == 0) {
            logger.warn("Proposal {} had left CONFIRMING before its outcome was recorded", proposalId)
        }
    }

    private fun ownProposal(authId: String, proposalId: UUID): BuddyActionProposal {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }
        return buddyActionProposalRepository
            .findById(proposalId)
            .orElse(null)
            ?.takeIf { it.userId == userId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No proposal $proposalId")
    }

    private fun alreadyDecided(status: BuddyProposalStatus): String =
        when (status) {
            BuddyProposalStatus.PROPOSED -> "This was already confirmed or dismissed."
            BuddyProposalStatus.CONFIRMING -> "This is already being done."
            BuddyProposalStatus.CONFIRMED -> "This was already done."
            BuddyProposalStatus.DISMISSED -> "This was dismissed, so nothing changed."
            BuddyProposalStatus.EXPIRED -> EXPIRED_MESSAGE
            BuddyProposalStatus.FAILED -> "This did not go through earlier. Ask the buddy again if you still want it."
        }

    /** What proposing produced: the line for the model, and the stored proposal to show when there is one. */
    data class ProposeOutcome(
        val toolResult: String,
        val proposal: BuddyActionProposal?,
    )

    private data class Outcome(
        val ok: Boolean,
        val message: String,
    )

    companion object {
        /**
         * How long a proposal can be confirmed. A day covers a manager who reads it later the same day,
         * while a preview written about last week's state is not confirmed as if it were current.
         */
        val TIME_TO_LIVE: Duration = Duration.ofHours(24)

        private const val EXPIRED_MESSAGE =
            "This proposal expired before it was confirmed. Ask the buddy again if you still want it."
    }
}
