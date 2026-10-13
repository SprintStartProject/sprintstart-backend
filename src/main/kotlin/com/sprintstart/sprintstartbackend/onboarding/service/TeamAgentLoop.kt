package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentResponse
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCitationDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyVocabularyDto
import com.sprintstart.sprintstartbackend.onboarding.model.ContributionWording
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySessionFilters
import kotlinx.coroutines.flow.FlowCollector
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * The agent loop of one team-mode reply: asks the AI for a step, runs the tools it asks for, and repeats
 * until it answers or the step budget is spent.
 *
 * [BuddyTeamService] owns the session, the transcript and the authorisation; this class owns the hops.
 */
@Component
class TeamAgentLoop(
    private val onboardingAiClient: OnboardingAiClient,
    private val buddyTeamTools: BuddyTeamTools,
    private val teamToolRunner: TeamToolRunner,
    private val artifactLookupService: ArtifactLookupService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Runs hops until [turn] is finished, then streams the reply's end and its sources. */
    suspend fun FlowCollector<BuddyStreamEvent>.runTurn(turn: TeamAgentTurn) {
        while (!turn.isFinished) {
            runHop(turn)
        }
        completeReply(turn.answer, turn.emittedContent)
        emitCitationsAndDone(resolveCitations(turn.citations))
    }

    private suspend fun FlowCollector<BuddyStreamEvent>.runHop(turn: TeamAgentTurn) {
        turn.step++
        // Per hop, because an area opened on the previous hop is mounted from this one.
        val tools = if (turn.capabilitiesEnabled) buddyTeamTools.toolSpecs(turn.areas.mounted) else emptyList()
        val response = relayAgentTurn(
            onboardingAiClient.buddyAgentTurnStream(agentRequest(turn, tools)),
            turn.emittedContent,
        )
        turn.citations = response.citations

        when {
            response.final && response.text.writesOutAToolCall() -> askAgainAfterWrittenOutCall(turn, response, tools)
            response.final -> turn.answer = response.text
            else -> turn.messages = answerToolCalls(turn, response, tools)
        }
    }

    private fun agentRequest(turn: TeamAgentTurn, tools: List<BuddyToolSpecDto>): BuddyAgentRequest =
        BuddyAgentRequest(
            messages = turn.messages,
            backendTools = tools,
            priorSummary = if (turn.step == 1) turn.priorSummary else null,
            vocabulary = VOCABULARY,
            projectIds = listOf(turn.context.projectId.toString()),
            capabilitiesEnabled = turn.capabilitiesEnabled,
            teamMode = true,
            filters = turn.filters,
        )

    /**
     * Treats a tool call written out as text as a failed step, not an answer.
     *
     * Its words were already streamed, so the client is told to take them back and the stored reply starts over.
     */
    private suspend fun FlowCollector<BuddyStreamEvent>.askAgainAfterWrittenOutCall(
        turn: TeamAgentTurn,
        response: BuddyAgentResponse,
        tools: List<BuddyToolSpecDto>,
    ) {
        logger.warn("Team buddy wrote a tool call out as its reply; asking again")
        emit(BuddyStreamEvent(type = BuddyService.RESET))
        turn.emittedContent.setLength(0)
        val retry = teamToolRunner.openAreasWrittenOut(
            response.text,
            tools.map { it.name }.toSet(),
            turn.areas,
        ) { emit(it) }
        turn.messages = response.messages.ifEmpty {
            turn.messages + BuddyAgentMessageDto(role = BuddyAgentMessageDto.ROLE_ASSISTANT, content = response.text)
        } + BuddyAgentMessageDto(role = BuddyAgentMessageDto.ROLE_USER, content = retry)
    }

    private suspend fun FlowCollector<BuddyStreamEvent>.answerToolCalls(
        turn: TeamAgentTurn,
        response: BuddyAgentResponse,
        tools: List<BuddyToolSpecDto>,
    ): List<BuddyAgentMessageDto> {
        val mounted = tools.map { it.name }.toSet()
        val results = response.pendingToolCalls.map { call ->
            BuddyAgentMessageDto(
                role = BuddyAgentMessageDto.ROLE_TOOL,
                content = teamToolRunner.run(call, turn.context, mounted, turn.areas) { emit(it) },
                toolCallId = call.id,
            )
        }
        return response.messages + results
    }

    private fun resolveCitations(citations: List<BuddyCitationDto>): List<ResolvedBuddyCitation> =
        citations.mapNotNull { citation ->
            val artifactId = citation.artifactId?.let(::parseUuidOrNull)
            val resolved = artifactId?.let(artifactLookupService::resolve)

            if (artifactId == null || resolved == null) {
                logger.warn("Could not resolve artifact {} for buddy citation", citation.artifactId)
                null
            } else {
                ResolvedBuddyCitation(
                    artifactId = artifactId,
                    filename = resolved.filename,
                    sourceUrl = resolved.sourceUrl,
                    startLine = citation.startLine,
                    startPage = citation.startPage,
                )
            }
        }

    private companion object {
        val VOCABULARY = BuddyVocabularyDto(
            contributionNoun = ContributionWording.NOUN,
            contributionNounPlural = ContributionWording.NOUN_PLURAL,
            contributionVerbPast = ContributionWording.VERB_PAST,
        )
    }
}

/** What one manager message carries from hop to hop of the agent loop. */
class TeamAgentTurn(
    val context: TeamToolContext,
    val capabilitiesEnabled: Boolean,
    val filters: BuddySessionFilters?,
    val priorSummary: String?,
    var messages: List<BuddyAgentMessageDto>,
    val areas: OpenAreas,
) {
    var citations: List<BuddyCitationDto> = emptyList()

    val emittedContent = StringBuilder()
    var answer: String? = null
    var step = 0

    val isFinished: Boolean
        get() = answer != null || step >= BuddyService.MAX_AGENT_STEPS
}
