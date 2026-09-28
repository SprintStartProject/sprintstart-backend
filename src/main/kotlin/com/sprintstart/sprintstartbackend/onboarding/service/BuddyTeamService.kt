package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCitationDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyVocabularyDto
import com.sprintstart.sprintstartbackend.onboarding.model.ContributionWording
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyTeamSession
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toAgentMessage
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyTeamMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyTeamSessionRepository
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Team mode: a project manager's buddy conversation about one project's team.
 *
 * The same visit model as the hire's buddy ([BuddyService]) — a durable transcript, a memory note
 * folded in the background, a greeting that opens a visit — with three differences that are the
 * point of it:
 *
 * - **Authorisation per turn.** Every entry point confirms the caller manages the project, through
 *   [UserApi.canManageProject], before anything is read. Losing the assignment refuses the next
 *   turn rather than leaving a manager with tools for a team that is no longer theirs.
 * - **Its own conversation per project.** Team talk never lands in the manager's own onboarding
 *   memory, and one project's note never grounds a turn about another.
 * - **Tools resolved per hop.** `open_area` changes what the next hop may call, so the tool list is
 *   rebuilt on every step of the agent loop instead of once per turn.
 */
@Service
@Suppress("TooManyFunctions") // The same surface as BuddyService — open, read, speak — plus its loop helpers.
class BuddyTeamService(
    private val buddyTeamSessionRepository: BuddyTeamSessionRepository,
    private val buddyTeamMessageRepository: BuddyTeamMessageRepository,
    private val onboardingAiClient: OnboardingAiClient,
    private val buddyTeamTools: BuddyTeamTools,
    private val buddyProposalService: BuddyProposalService,
    private val userApi: UserApi,
    private val buddyCompactionService: BuddyCompactionService,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the current visit of the caller's team conversation about [projectId], oldest first.
     *
     * @throws ResponseStatusException 404 if the user does not exist; 403 if they do not manage the project.
     */
    fun getMessagesForMe(authId: String, projectId: UUID): List<BuddyMessageResponse> {
        val userId = authorize(authId, projectId)
        val session = buddyTeamSessionRepository.findByUserIdAndProjectId(userId, projectId)
            ?: return emptyList()
        val messages = buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
        return messages
            .drop(messages.indexOfLast { it.opening }.takeIf { it >= 0 } ?: 0)
            .map { it.toResponse() }
    }

    /**
     * Opens a team-mode visit, streaming a greeting grounded in the team's attention list.
     *
     * Follows [BuddyService.streamOpenForMe] rule for rule: an existing greeting is replayed whole, a
     * stream that broke keeps what was read, one that produced nothing persists nothing.
     *
     * @throws ResponseStatusException 404 if the user does not exist; 403 if they do not manage the project.
     */
    suspend fun streamOpenForMe(authId: String, projectId: UUID): Flow<BuddyStreamEvent> {
        val userId = authorize(authId, projectId)
        val session = getOrCreateSession(userId, projectId)
        val all = buddyTeamMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)

        val greetingAlreadyThere = all.lastOrNull()?.takeIf { it.opening }
        if (greetingAlreadyThere != null) {
            return flowOf(
                BuddyStreamEvent(type = BuddyService.TOKEN, content = greetingAlreadyThere.content),
                BuddyStreamEvent(type = BuddyService.DONE),
            )
        }

        val recent = all.drop(session.summarizedCount).map { it.toAgentMessage() }
        val state = buddyTeamTools.teamSnapshot(projectId)

        return flow {
            val streamed = StringBuilder()
            var opening: BuddyOpenStreamEvent? = null
            try {
                onboardingAiClient
                    .streamBuddyOpen(
                        BuddyOpenRequest(memory = session.summary, recent = recent, state = state, teamMode = true),
                    ).collect { event ->
                        when (event.type) {
                            BuddyOpenStreamEvent.TOKEN -> event.content?.let {
                                streamed.append(it)
                                emit(BuddyStreamEvent(type = BuddyService.TOKEN, content = it))
                            }

                            BuddyOpenStreamEvent.DONE -> opening = event
                        }
                    }
            } catch (@Suppress("SwallowedException") e: OnboardingAiException) {
                // Opening the buddy must never fail the page.
                logger.warn("Team buddy open stream failed: {}", e.message)
            }

            finishOpen(session, streamed.toString(), opening)
            compactInBackground(userId, projectId)
        }
    }

    /**
     * Sends the manager's message in team mode and streams the reply.
     *
     * With [capabilitiesEnabled] false no tools are mounted, exactly as for the hire's buddy.
     *
     * @throws ResponseStatusException 404 if the user does not exist; 403 if they do not manage the project.
     */
    suspend fun sendMessageForMe(
        authId: String,
        projectId: UUID,
        content: String,
        capabilitiesEnabled: Boolean = true,
    ): Flow<BuddyStreamEvent> {
        val userId = authorize(authId, projectId)
        val session = getOrCreateSession(userId, projectId)

        // Read before saving the new message so it is not sent to the AI twice.
        val history = buddyTeamMessageRepository
            .findAllBySessionIdOrderByCreatedAtAsc(session.id)
            .drop(session.summarizedCount)
            .map { it.toAgentMessage() }

        buddyTeamMessageRepository.save(
            BuddyTeamMessage(session = session, role = BuddyMessageRole.USER, content = content),
        )

        val context = TeamToolContext(userId = userId, authId = authId, projectId = projectId)

        return flow {
            var messages = history + BuddyAgentMessageDto(role = "user", content = content)
            var citations: List<BuddyCitationDto> = emptyList()
            var answer: String? = null
            var step = 0
            val openedAreas = mutableSetOf<TeamArea>()

            while (answer == null && step < BuddyService.MAX_AGENT_STEPS) {
                step++
                // Per hop, not per turn: an area opened on the previous hop is mounted from this one.
                val tools = if (capabilitiesEnabled) buddyTeamTools.toolSpecs(openedAreas) else emptyList()
                val response = onboardingAiClient.buddyAgentTurn(
                    BuddyAgentRequest(
                        messages = messages,
                        backendTools = tools,
                        priorSummary = if (step == 1) session.summary else null,
                        vocabulary = VOCABULARY,
                        // Retrieval is scoped to the one project this conversation is about.
                        projectIds = listOf(projectId.toString()),
                        capabilitiesEnabled = capabilitiesEnabled,
                        teamMode = true,
                    ),
                )
                citations = response.citations
                if (response.final) {
                    answer = response.text
                } else {
                    val mounted = tools.map { it.name }.toSet()
                    val next = response.messages.toMutableList()
                    for (call in response.pendingToolCalls) {
                        next.add(
                            BuddyAgentMessageDto(
                                role = "tool",
                                content = runToolCall(call, context, mounted, openedAreas),
                                toolCallId = call.id,
                            ),
                        )
                    }
                    messages = next
                }
            }

            val reply = answer?.takeIf { it.isNotBlank() } ?: BuddyService.FALLBACK_REPLY
            emitAgentReply(reply, citations)

            buddyTeamMessageRepository.save(
                BuddyTeamMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = reply),
            )
            compactInBackground(userId, projectId)
        }
    }

    /**
     * Runs one tool the AI asked for in team mode.
     *
     * A mounted action never runs here: it becomes a stored proposal and an `action_proposal` event the
     * manager confirms or dismisses, and the model is told it was offered, not done. `open_area` changes
     * what later hops mount. Every other tool is executed by [BuddyTeamTools], which refuses anything not
     * mounted on this hop — an action name that was not mounted ends up there too, and is refused.
     */
    private suspend fun FlowCollector<BuddyStreamEvent>.runToolCall(
        call: BuddyToolCallDto,
        context: TeamToolContext,
        mountedToolNames: Set<String>,
        openedAreas: MutableSet<TeamArea>,
    ): String {
        if (call.name in mountedToolNames && buddyProposalService.isAction(call.name)) {
            val outcome = buddyProposalService.propose(call, context)
            outcome.proposal?.let { proposal ->
                emit(
                    BuddyStreamEvent(
                        type = "action_proposal",
                        action = proposal.action,
                        label = proposal.label,
                        proposalId = proposal.id.toString(),
                        preview = proposal.preview,
                        risk = proposal.risk.name,
                    ),
                )
            }
            return outcome.toolResult
        }
        emit(BuddyStreamEvent(type = "tool_use", name = call.name, kind = "tool"))
        if (call.name == BuddyTeamTools.OPEN_AREA && call.name in mountedToolNames) {
            val outcome = buddyTeamTools.openArea(call)
            outcome.area?.let { openedAreas.add(it) }
            return outcome.toolResult
        }
        return buddyTeamTools.execute(call, context, mountedToolNames)
    }

    private suspend fun FlowCollector<BuddyStreamEvent>.finishOpen(
        session: BuddyTeamSession,
        streamed: String,
        opening: BuddyOpenStreamEvent?,
    ) {
        val greeting = opening?.greeting?.takeIf { it.isNotBlank() } ?: streamed

        if (greeting.isBlank()) {
            // Nothing reached the manager, so nothing is persisted: a stored fallback would become
            // this visit's permanent greeting.
            emit(BuddyStreamEvent(type = BuddyService.TOKEN, content = FALLBACK_TEAM_OPENING))
            emit(BuddyStreamEvent(type = BuddyService.DONE))
            return
        }

        buddyTeamMessageRepository.save(
            BuddyTeamMessage(
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = greeting,
                opening = true,
            ),
        )

        opening?.action?.let {
            emit(BuddyStreamEvent(type = BuddyService.OPENING_ACTION, label = it.label, question = it.question))
        }
        emit(BuddyStreamEvent(type = BuddyService.DONE))
    }

    private fun getOrCreateSession(userId: UUID, projectId: UUID): BuddyTeamSession =
        buddyTeamSessionRepository.findByUserIdAndProjectId(userId, projectId)
            ?: buddyTeamSessionRepository.save(BuddyTeamSession(userId = userId, projectId = projectId))

    /** Fire-and-forget, for the same reason as [BuddyService]: nobody may wait on a fold. */
    private fun compactInBackground(userId: UUID, projectId: UUID) {
        applicationScope.launch {
            buddyCompactionService.compactTeamIfNeeded(userId, projectId)
        }
    }

    /**
     * Resolves the caller and confirms they manage [projectId].
     *
     * @throws ResponseStatusException 404 if the user does not exist; 403 if they do not manage the project.
     */
    private fun authorize(authId: String, projectId: UUID): UUID {
        val userId = userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }
        if (!userApi.canManageProject(authId, projectId)) {
            throw ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Team mode is only available for a project you manage, and you do not manage project $projectId.",
            )
        }
        return userId
    }

    private companion object {
        val VOCABULARY = BuddyVocabularyDto(
            contributionNoun = ContributionWording.NOUN,
            contributionNounPlural = ContributionWording.NOUN_PLURAL,
            contributionVerbPast = ContributionWording.VERB_PAST,
        )

        const val FALLBACK_TEAM_OPENING =
            "You're in team mode. Ask me who needs your attention, or how somebody on the team is getting on."
    }
}
