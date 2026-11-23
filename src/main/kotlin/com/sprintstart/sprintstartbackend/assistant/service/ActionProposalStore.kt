package com.sprintstart.sprintstartbackend.assistant.service

import com.sprintstart.sprintstartbackend.assistant.external.ActionProposal
import com.sprintstart.sprintstartbackend.assistant.external.ActionProposalApi
import com.sprintstart.sprintstartbackend.assistant.external.BuddyActionResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyProposalStatus
import com.sprintstart.sprintstartbackend.assistant.external.ProposalOffer
import com.sprintstart.sprintstartbackend.assistant.model.entity.BuddyActionProposal
import com.sprintstart.sprintstartbackend.assistant.repository.BuddyActionProposalRepository
import com.sprintstart.sprintstartbackend.shared.ids.ActionProposalId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Clock

/** [ActionProposalApi], stored in `buddy_action_proposals`. */
@Service
internal class ActionProposalStore(
    private val buddyActionProposalRepository: BuddyActionProposalRepository,
    private val clock: Clock,
) : ActionProposalApi {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    override fun open(userId: UserId, projectId: ProjectId, offer: ProposalOffer): ActionProposal {
        val now = clock.instant()
        val stored = buddyActionProposalRepository.save(
            BuddyActionProposal(
                userId = userId.value,
                projectId = projectId.value,
                action = offer.action,
                params = json.encodeToString(JsonObject.serializer(), offer.params),
                label = offer.label,
                preview = offer.preview,
                risk = offer.risk,
                createdAt = now,
                expiresAt = now.plus(ActionProposalApi.TIME_TO_LIVE),
            ),
        )
        return stored.toProposal(offer.params)
    }

    override suspend fun confirm(
        userId: UserId,
        proposalId: ActionProposalId,
        run: suspend (ActionProposal) -> BuddyActionResponse,
    ): BuddyActionResponse {
        val proposal = owned(userId, proposalId)
        claim(proposal)?.let { return it }
        return settle(proposalId) { run(proposal.toProposal(params(proposal))) }
    }

    override fun dismiss(userId: UserId, proposalId: ActionProposalId): BuddyActionResponse =
        decline(owned(userId, proposalId))

    /** @throws ResponseStatusException 404 when the proposal does not exist or is not [userId]'s. */
    private fun owned(userId: UserId, proposalId: ActionProposalId): BuddyActionProposal =
        buddyActionProposalRepository
            .findById(proposalId.value)
            .orElse(null)
            ?.takeIf { it.userId == userId.value }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "No proposal ${proposalId.value}")

    private fun params(proposal: BuddyActionProposal): JsonObject = json.parseToJsonElement(proposal.params).jsonObject

    /**
     * Claims an open proposal for running, with one conditional update, so a confirm that loses a race runs
     * nothing. An expired proposal is marked so on the way.
     *
     * @return `null` once claimed, otherwise the refusal to show.
     */
    private fun claim(proposal: BuddyActionProposal): BuddyActionResponse? {
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
        return if (claimed == 0) {
            BuddyActionResponse(ok = false, message = "This was already confirmed or dismissed.")
        } else {
            null
        }
    }

    /**
     * Runs a claimed proposal and records `CONFIRMED` or `FAILED`, even if [run] throws.
     *
     * A [ResponseStatusException] from [run] is a handled failure: its reason is shown and the proposal is
     * marked failed.
     */
    private suspend fun settle(
        proposalId: ActionProposalId,
        run: suspend () -> BuddyActionResponse,
    ): BuddyActionResponse {
        var outcome = BuddyActionResponse(ok = false, message = "Something went wrong, so this was not done.")
        try {
            outcome = run()
        } catch (e: ResponseStatusException) {
            outcome = BuddyActionResponse(ok = false, message = e.reason ?: "That did not go through.")
        } finally {
            // Also reached on an unexpected exception, which then propagates: the proposal is never left claimed.
            recordOutcome(proposalId, outcome)
        }
        return outcome
    }

    private fun decline(proposal: BuddyActionProposal): BuddyActionResponse {
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

    /**
     * Records how a claimed proposal ended.
     *
     * The action may already have committed, so a failure to record it is logged rather than thrown: the
     * user must not be told a change failed that in fact happened.
     */
    private fun recordOutcome(proposalId: ActionProposalId, outcome: BuddyActionResponse) {
        val recorded = try {
            buddyActionProposalRepository.finish(
                proposalId.value,
                BuddyProposalStatus.CONFIRMING,
                if (outcome.ok) BuddyProposalStatus.CONFIRMED else BuddyProposalStatus.FAILED,
                clock.instant(),
                outcome.message,
            )
        } catch (e: DataAccessException) {
            logger.error("Could not record the outcome of proposal {}; it stays CONFIRMING", proposalId.value, e)
            return
        }
        if (recorded == 0) {
            logger.warn("Proposal {} had left CONFIRMING before its outcome was recorded", proposalId.value)
        }
    }

    private fun BuddyActionProposal.toProposal(params: JsonObject) =
        ActionProposal(
            id = id,
            userId = userId,
            projectId = projectId,
            action = action,
            params = params,
            label = label,
            preview = preview,
            risk = risk,
        )

    private fun alreadyDecided(status: BuddyProposalStatus): String =
        when (status) {
            BuddyProposalStatus.PROPOSED -> "This was already confirmed or dismissed."
            BuddyProposalStatus.CONFIRMING -> "This is already being done."
            BuddyProposalStatus.CONFIRMED -> "This was already done."
            BuddyProposalStatus.DISMISSED -> "This was dismissed, so nothing changed."
            BuddyProposalStatus.EXPIRED -> EXPIRED_MESSAGE
            BuddyProposalStatus.FAILED -> "This did not go through earlier. Ask the buddy again if you still want it."
        }

    companion object {
        private const val EXPIRED_MESSAGE =
            "This proposal expired before it was confirmed. Ask the buddy again if you still want it."
    }
}
