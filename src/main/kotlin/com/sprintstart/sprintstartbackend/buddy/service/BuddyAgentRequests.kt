package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.arrival.external.ContributionWording
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.assistant.external.BuddySessionFilters
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyVocabularyDto
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession

/** The contribution vocabulary, in the shape the AI service's persona skeleton expects. */
internal fun buddyVocabulary(): BuddyVocabularyDto =
    BuddyVocabularyDto(
        contributionNoun = ContributionWording.NOUN,
        contributionNounPlural = ContributionWording.NOUN_PLURAL,
        contributionVerbPast = ContributionWording.VERB_PAST,
    )

/**
 * What every hop of one agent loop is allowed to see and do: the tools on offer, the retrieval
 * scope and the mode. All of it is resolved once per turn and sent on every hop.
 */
internal data class BuddyTurnScope(
    val tools: List<BuddyToolSpecDto>,
    val vocabulary: BuddyVocabularyDto,
    val projectIds: List<String>,
    val capabilitiesEnabled: Boolean,
    val filters: BuddySessionFilters?,
)

/**
 * Builds one agent request. The summary goes on the first hop only: after that the AI has
 * folded it into the running conversation it returns, and re-sending would double-fold
 * messages already inside it.
 *
 * Nothing here may ask the AI to fold, and neither side carries a field for it: a fold
 * runs before the reply is composed, putting a model call in front of the answer. See
 * [BuddyService.sendMessageForMe] and [BuddyCompactionService].
 */
internal fun buddyAgentRequest(
    messages: List<BuddyAgentMessageDto>,
    step: Int,
    session: BuddySession,
    scope: BuddyTurnScope,
): BuddyAgentRequest =
    BuddyAgentRequest(
        messages = messages,
        backendTools = scope.tools,
        priorSummary = if (step == 1) session.summary else null,
        // Sent on every hop, unlike the summary: the persona is rebuilt from scratch whenever
        // the running conversation has no system message yet, so withholding it after the
        // first hop would let a resumed turn fall back to the engineering wording.
        vocabulary = scope.vocabulary,
        // Same reason, and the same every hop: retrieval happens on the AI side on any hop the
        // model chooses to search, so a scope sent only on the first would silently widen.
        projectIds = scope.projectIds,
        // Every hop too, and for the same reason as the persona: a resumed turn that lost the
        // mode would rebuild a mentor offering to act, mid-conversation with a hire who asked
        // it not to.
        capabilitiesEnabled = scope.capabilitiesEnabled,
        filters = scope.filters,
    )
