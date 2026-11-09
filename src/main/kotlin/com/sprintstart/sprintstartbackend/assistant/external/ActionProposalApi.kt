package com.sprintstart.sprintstartbackend.assistant.external

import com.sprintstart.sprintstartbackend.shared.ids.ActionProposalId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import kotlinx.serialization.json.JsonObject
import java.time.Duration
import java.util.UUID

/**
 * The life of a change an assistant offers: stored when offered, claimed once when confirmed, and settled.
 *
 * Which actions exist and what they do belongs to the assistant that offers them. The kernel only makes
 * sure a proposal runs at most once, never after it expired, and never stays claimed.
 *
 * A call that names a proposal throws a 404 `ResponseStatusException` when it does not exist or is not the
 * given user's, so a proposal id says nothing about somebody else's proposals.
 */
interface ActionProposalApi {
    /** Stores [offer] as an open proposal for [userId] in [projectId], valid for [TIME_TO_LIVE]. */
    fun open(userId: UserId, projectId: ProjectId, offer: ProposalOffer): ActionProposal

    /**
     * Claims [userId]'s open proposal once and settles it with what [run] answers.
     *
     * A proposal already decided or expired is refused with a sentence, without calling [run]. The outcome
     * is recorded as `CONFIRMED` or `FAILED` even if [run] throws; a
     * [org.springframework.web.server.ResponseStatusException] from [run] is a handled failure whose reason
     * is shown.
     */
    suspend fun confirm(
        userId: UserId,
        proposalId: ActionProposalId,
        run: suspend (ActionProposal) -> BuddyActionResponse,
    ): BuddyActionResponse

    /** Declines [userId]'s proposal. Nothing changes but the proposal itself. */
    fun dismiss(userId: UserId, proposalId: ActionProposalId): BuddyActionResponse

    companion object {
        /** Long enough to read it later the same day, short enough that a stale preview is not confirmed. */
        val TIME_TO_LIVE: Duration = Duration.ofHours(24)
    }
}

/**
 * What a proposal offers.
 *
 * @property action The tool name that identifies the action when the proposal is confirmed.
 * @property label The confirm button, naming the action and its target.
 * @property preview Everything the user is agreeing to, in words.
 */
data class ProposalOffer(
    val action: String,
    val risk: BuddyProposalRisk,
    val params: JsonObject,
    val label: String,
    val preview: String,
)

/** A stored proposal, as the assistant that offered it shows and runs it. */
data class ActionProposal(
    val id: UUID,
    val userId: UUID,
    val projectId: UUID,
    val action: String,
    val params: JsonObject,
    val label: String,
    val preview: String,
    val risk: BuddyProposalRisk,
)
