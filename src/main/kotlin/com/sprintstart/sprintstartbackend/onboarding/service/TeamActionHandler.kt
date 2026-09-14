package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyProposalRisk
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import kotlinx.serialization.json.JsonObject

/**
 * One action the team-mode buddy may offer a project's manager, in three steps that never blur.
 *
 * 1. [draft] turns the model's call into a proposal — or a reason it cannot be offered — and changes
 *    nothing. The proposal is stored with the [JsonObject] this returns.
 * 2. [recheck] runs when the manager confirms, against what was stored: the target may have changed or
 *    gone since the preview was written.
 * 3. [perform] makes the change.
 *
 * Mounted like an area's read tools: only once its [area] has been opened. The manager was confirmed
 * as the project's manager before [draft] and again before [recheck]; every implementation must still
 * check that its target belongs to [TeamToolContext.projectId], because an id in [JsonObject] came from
 * the model.
 */
interface TeamActionHandler {
    val area: TeamArea

    /** Declared here, never taken from the model. */
    val risk: BuddyProposalRisk

    /** The tool the model calls; its name is the action's identity in stored proposals. */
    val spec: BuddyToolSpecDto

    fun draft(call: BuddyToolCallDto, context: TeamToolContext): TeamActionDraft

    /** @return `null` when the stored proposal can still run, otherwise the reason it cannot. */
    fun recheck(params: JsonObject, context: TeamToolContext): String?

    /**
     * Makes the change and returns the line to show the manager.
     *
     * @throws org.springframework.web.server.ResponseStatusException for a handled failure; its reason
     * is shown to the manager and the proposal is marked failed.
     */
    fun perform(params: JsonObject, context: TeamToolContext): String
}

/** What [TeamActionHandler.draft] made of a call. */
sealed interface TeamActionDraft {
    /**
     * An action ready to offer.
     *
     * @property label The confirm button, naming the action and its target.
     * @property preview Everything the manager is agreeing to, in words — the full text of an answer,
     * the person who loses access.
     */
    data class Proposed(
        val params: JsonObject,
        val label: String,
        val preview: String,
    ) : TeamActionDraft

    /** Not offerable, with a reason the model can act on. */
    data class Refused(
        val reason: String,
    ) : TeamActionDraft
}
