package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.client.BuddyAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.event.QuestionAskedEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCitationDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolCallDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyVocabularyDto
import com.sprintstart.sprintstartbackend.onboarding.model.ContributionWording
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyCitation
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySessionFilters
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySessionStatus
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.AiResponseException
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toAgentMessage
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.AiGenerateSessionTitleRequest
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.CreateSessionResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.GetSessionsResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyCitationRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Manages a hire's ongoing onboarding buddy conversations: durable across visits, backed by the stateless AI
 * buddy-agent endpoint.
 *
 * The buddy is a tool-using agent. This service runs the agent loop: it asks the AI to reason over
 * the conversation (with the backend tools it may call), executes any tool the AI hands back —
 * strictly on behalf of the resolved caller — feeds each result in, and repeats until the AI has a
 * final answer. The AI stays stateless; the running [BuddyAgentMessageDto] list lives here for the
 * length of one reply. Corpus questions are answered AI-side via ``search_docs``; questions about
 * the hire's own onboarding are answered by [BuddyToolExecutor].
 */
@Service
@Suppress("TooManyFunctions")
class BuddyService(
    private val buddySessionRepository: BuddySessionRepository,
    private val buddyMessageRepository: BuddyMessageRepository,
    private val buddyCitationRepository: BuddyCitationRepository,
    private val onboardingAiClient: OnboardingAiClient,
    private val buddyToolExecutor: BuddyToolExecutor,
    private val buddyActionService: BuddyActionService,
    private val userApi: UserApi,
    private val artifactLookupService: ArtifactLookupService,
    private val compactionScheduler: BuddyCompactionScheduler,
    private val buddyAiClient: BuddyAiClient,
    private val eventPublisher: ApplicationEventPublisher,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Finds a user's ongoing buddy sessions. */
    fun getSessions(authId: String): GetSessionsResponse {
        val userId = resolveUserId(authId)
        val sessions = buddySessionRepository
            .findByUserIdAndStatusOrderByCreatedAtDesc(
                userId,
                BuddySessionStatus.ACTIVE,
            ).map { it.toResponse() }
            .toList()
        return GetSessionsResponse(sessions)
    }

    /**
     * Returns a session's buddy messages, oldest first.
     *
     * The boundary is the last opening marker, never [BuddySession.summarizedCount].
     * Keying it to the compaction cursor makes a hire's own scrollback shrink as the model folds.
     */
    fun getMessagesForMe(authId: String, sessionId: UUID?): List<BuddyMessageResponse> {
        val userId = resolveUserId(authId)
        if (sessionId == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId required")
        }
        val session = buddySessionRepository.findByIdAndUserId(sessionId, userId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Buddy session not found for current user")
        return buddyMessageRepository
            .findAllBySessionIdOrderByCreatedAtAsc(session.id)
            .sinceLastOpening { it.opening }
            .map { it.toResponse() }
    }

    /**
     * Creates a new session for the current user.
     *
     * The frontend creates sessions without a project, so a missing [projectId] is filled in here:
     * a user on exactly one project gets that project, which is what feeds the FAQ and the question
     * counts (both are derived from the session's project). A user on several projects (a PM) or on
     * none gets a session without one, since there is no single project to pick.
     *
     * @param authId The ID of the currently authenticated user.
     * @param projectId The (optional) ID of the project the session is linked to.
     */
    fun createSession(authId: String, projectId: UUID?): CreateSessionResponse {
        val userId = resolveUserId(authId)
        val session = BuddySession(
            userId = userId,
            projectId = projectId ?: singleProjectOf(projectIdsFor(userId)),
        )
        buddySessionRepository.save(session)
        return CreateSessionResponse(session.id)
    }

    /**
     * Sends the authenticated user's message to the buddy and streams the reply.
     *
     * With [capabilitiesEnabled] false the mentor answers from the project's material and does
     * nothing else: no tools are mounted, so none can be called, and the mode is enforced by what
     * the model was given rather than by what the prompt asked of it. Retrieval is unaffected —
     * `search_docs` runs AI-side — so this costs the hire nothing in what they can find.
     *
     * A hire on no project is refused rather than answered. Retrieval is scoped to their projects
     * and the AI admits nothing from an empty scope, so the turn would search nothing and reply as
     * though the project had no material — the worst moment to sound confident and the hardest
     * state for the hire to diagnose. They are told that instead, and what resolves it. See
     * [projectIdsFor].
     *
     * A session bound to a project the user has since left is rebound before anything else runs:
     * to the user's only project, or to none when there is no single one. Otherwise an old session
     * would keep retrieving from, and asking its questions into, the previous project. The older
     * questions of that session then count for the new project, because the question counts are
     * derived from the session. A session without a project is never bound after the fact.
     *
     * Per message rather than per session. A hire who looked something up and then wants the mentor
     * back should not have to remember which state a switch was left in, and the transcript stays
     * one conversation across the change.
     *
     * The user's message is persisted immediately. The agent turn is relayed live, hop by hop, and the
     * reply stored is exactly what was streamed to the hire — the words of every hop, so a reload
     * reads as the turn did. A stream that errors or is cancelled at any point of the loop keeps what
     * had been shown as an incomplete reply, and stores nothing if nothing had been shown.
     *
     * The AI never receives the whole transcript: only the window after the session's
     * [BuddySession.summarizedCount] cursor, plus the running summary standing in for the rest.
     *
     * This turn must never ask the AI to fold. A fold runs before the reply is composed, and
     * since the cursor advances by exactly what it folds, the window would then sit at [WINDOW]
     * permanently — an extra serialized model call on every turn. [BuddyCompactionService] folds
     * afterward instead; a fold that has not happened yet just means a longer window this turn.
     *
     * @throws ResponseStatusException 404 if the authenticated user doesn't exist.
     */
    @Suppress("CyclomaticComplexMethod", "ThrowsCount")
    suspend fun sendMessageForMe(
        authId: String,
        sessionId: UUID?,
        content: String,
        capabilitiesEnabled: Boolean = true,
        filters: BuddySessionFilters?,
        currentPage: String? = null,
    ): Flow<BuddyStreamEvent> {
        val userId = resolveUserId(authId)
        if (sessionId == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId required")
        }
        val session = buddySessionRepository.findByIdAndUserId(sessionId, userId) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Session not found for current user",
        )

        // Resolved once per turn and reused for the retrieval scope below. A session whose project
        // the user has left is rebound before the question event, so the event names the right one.
        val memberProjects = projectIdsFor(userId)
        if (session.projectId != null && session.projectId.toString() !in memberProjects) {
            session.projectId = singleProjectOf(memberProjects)
            buddySessionRepository.save(session)
        }

        if (session.title.isBlank()) {
            try {
                val generatedTitle = buddyAiClient.getSessionTitle(AiGenerateSessionTitleRequest(content))
                buddySessionRepository.nameIfUntitled(session.id, generatedTitle.title)
            } catch (e: AiResponseException) {
                logger.warn("Could not generate buddy session title", e)
            }
        }

        // Read history before saving the new message so it isn't sent to the AI service twice.
        // Everything the summary already covers stays out of the prompt.
        val history = buddyMessageRepository
            .findAllBySessionIdOrderByCreatedAtAsc(session.id)
            .drop(session.summarizedCount)
            .map { it.toAgentMessage() }

        val message = BuddyMessage(
            session = session,
            role = BuddyMessageRole.USER,
            content = content,
        )

        buddyMessageRepository.save(message)

        val questionForFaq = stripQuotedSelection(message.content)

        session.projectId?.let { projectId ->
            eventPublisher.publishEvent(
                QuestionAskedEvent(
                    messageId = message.id,
                    sessionId = session.id,
                    projectId = projectId,
                    question = questionForFaq,
                    askedAt = message.createdAt,
                ),
            )
        }

        // The reasoner gets the read-only tools and the action tools it may propose; an action never mutates
        // here. With capabilities off the list is empty, so the mode is enforced by what the model is given,
        // not asked for in the prompt.
        val tools = if (capabilitiesEnabled) {
            buddyToolExecutor.toolSpecs(userId) + buddyActionService.actionSpecs(userId)
        } else {
            emptyList()
        }

        // Both resolved once per turn, not per hop: neither can change mid-conversation, and
        // re-reading would cost a membership lookup on every step of the agent loop.
        val vocabulary = vocabulary()
        val projectIds = session.projectId?.let { listOf(it.toString()) } ?: memberProjects

        // A hire on no project has no retrieval scope, and the AI fails closed on an empty one, so the turn
        // is refused here with the one thing that resolves it. The message is already persisted.
        if (projectIds.isEmpty()) {
            return flow {
                emitAgentReply(NO_PROJECT_REPLY, emptyList(), StringBuilder())
                buddyMessageRepository.save(
                    BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = NO_PROJECT_REPLY),
                )
            }
        }

        return flow {
            var messages = history + BuddyAgentMessageDto(role = BuddyAgentMessageDto.ROLE_USER, content = content)
            val citations = mutableListOf<BuddyCitationDto>()
            // Everything the hire has been shown of the reply, across every hop of the loop.
            val emittedContent = StringBuilder()
            var answer: String? = null
            var step = 0
            val resolvedCitations: List<ResolvedBuddyCitation>

            // The whole loop is guarded, not only the last emission: the words now leave while the
            // loop runs, so a stop or a failure at any hop leaves a partial reply worth keeping.
            try {
                while (answer == null && step < MAX_AGENT_STEPS) {
                    step++
                    val response = relayAgentTurn(
                        onboardingAiClient.buddyAgentTurnStream(
                            agentRequest(
                                messages,
                                tools,
                                step,
                                session,
                                vocabulary,
                                projectIds,
                                capabilitiesEnabled,
                                filters,
                            ),
                        ),
                        emittedContent,
                    )
                    citations += response.citations
                    if (response.final) {
                        answer = response.text
                    } else {
                        // The AI needs a backend tool run: execute each on the caller's behalf and feed
                        // the result back as a `tool` message appended to the running conversation.
                        val next = response.messages.toMutableList()
                        for (call in response.pendingToolCalls) {
                            next.add(
                                BuddyAgentMessageDto(
                                    role = BuddyAgentMessageDto.ROLE_TOOL,
                                    content = runToolCall(call, userId, currentPage),
                                    toolCallId = call.id,
                                ),
                            )
                        }
                        messages = next
                    }
                }

                completeReply(answer, emittedContent)

                resolvedCitations = citations.mapNotNull { citation ->
                    val artifactId = citation.artifactId?.let(::parseUuidOrNull)
                    val resolved = artifactId?.let(artifactLookupService::resolve)

                    if (artifactId == null || resolved == null) {
                        logger.warn(
                            "Could not resolve artifact {} for buddy citation",
                            citation.artifactId,
                        )
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

                emitCitationsAndDone(resolvedCitations)
            } catch (e: CancellationException) {
                saveIncompleteReply(session, emittedContent.toString())
                throw e
            } catch (e: Exception) {
                saveIncompleteReply(session, emittedContent.toString())
                throw e
            }

            val message = buddyMessageRepository.save(
                BuddyMessage(
                    session = session,
                    role = BuddyMessageRole.ASSISTANT,
                    content = emittedContent.toString(),
                ),
            )

            val citationEntities = resolvedCitations.map { citation ->
                BuddyCitation(
                    artifactId = citation.artifactId,
                    filename = citation.filename,
                    sourceUrl = citation.sourceUrl,
                    startLine = citation.startLine,
                    startPage = citation.startPage,
                    message = message,
                )
            }

            buddyCitationRepository.saveAll(citationEntities)
            // Only now, with the reply persisted: folding must never delay the answer.
            compactionScheduler.foldHireConversation(UserId(userId), BuddySessionId(session.id))
        }.endFailureWithErrorEvent()
    }

    private fun saveIncompleteReply(session: BuddySession, content: String) {
        if (content.isBlank()) {
            return
        }

        buddyMessageRepository.save(
            BuddyMessage(
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = content,
                isIncomplete = true,
            ),
        )
    }

    /** The contribution vocabulary, in the shape the AI service's persona skeleton expects. */
    private fun vocabulary(): BuddyVocabularyDto =
        BuddyVocabularyDto(
            contributionNoun = ContributionWording.NOUN,
            contributionNounPlural = ContributionWording.NOUN_PLURAL,
            contributionVerbPast = ContributionWording.VERB_PAST,
        )

    /**
     * Builds one agent request. The summary goes on the first hop only: after that the AI has
     * folded it into the running conversation it returns, and re-sending would double-fold
     * messages already inside it.
     *
     * Nothing here may ask the AI to fold, and neither side carries a field for it: a fold
     * runs before the reply is composed, putting a model call in front of the answer. See
     * [sendMessageForMe] and [BuddyCompactionService].
     */
    private fun agentRequest(
        messages: List<BuddyAgentMessageDto>,
        tools: List<BuddyToolSpecDto>,
        step: Int,
        session: BuddySession,
        vocabulary: BuddyVocabularyDto,
        projectIds: List<String>,
        capabilitiesEnabled: Boolean,
        filters: BuddySessionFilters?,
    ): BuddyAgentRequest =
        BuddyAgentRequest(
            messages = messages,
            backendTools = tools,
            priorSummary = if (step == 1) session.summary else null,
            // Sent on every hop, unlike the summary: the persona is rebuilt from scratch whenever
            // the running conversation has no system message yet, so withholding it after the
            // first hop would let a resumed turn fall back to the engineering wording.
            vocabulary = vocabulary,
            // Same reason, and the same every hop: retrieval happens on the AI side on any hop the
            // model chooses to search, so a scope sent only on the first would silently widen.
            projectIds = projectIds,
            // Every hop too, and for the same reason as the persona: a resumed turn that lost the
            // mode would rebuild a mentor offering to act, mid-conversation with a hire who asked
            // it not to.
            capabilitiesEnabled = capabilitiesEnabled,
            filters = filters,
        )

    /**
     * The projects whose material this hire may be shown, as ids.
     *
     * Every project they are on rather than one of them: the buddy is not a per-project surface,
     * and a hire onboarding on two projects asking "how do we deploy" means either.
     *
     * An empty list is not "search everything": the AI service fails closed on an empty scope and
     * admits nothing. That is the intended reading, not a limitation. The same empty list comes
     * back from a user record that has not synced, a membership lookup that returned nothing, and
     * an account mid-provisioning, so it is not evidence the hire may see everything — and reading
     * it as intent would turn a missing value into an authorization decision. The failure modes are
     * not symmetric: fail-open shows one project's material to somebody on another, while
     * fail-closed returns an empty answer. [sendMessageForMe] refuses the turn and tells the hire
     * rather than letting an empty scope pass silently.
     */
    private fun projectIdsFor(userId: UUID): List<String> =
        userApi
            .getUsersByIds(listOf(userId))
            .firstOrNull()
            ?.projects
            .orEmpty()
            .map { it.projectId.toString() }

    /** The project to bind a session to: the only one the user is on, or null when there is no single one. */
    private fun singleProjectOf(projectIds: List<String>): UUID? =
        projectIds.singleOrNull()?.let { UUID.fromString(it) }

    /**
     * Runs one tool the AI asked for, emitting the event(s) the client needs to see, and returns
     * the plain-text result fed back to the model. An action tool never mutates here: it produces
     * a proposal the hire gates behind a confirm button, and the AI is told it was proposed.
     */
    private suspend fun FlowCollector<BuddyStreamEvent>.runToolCall(
        call: BuddyToolCallDto,
        userId: UUID,
        currentPage: String?,
    ): String =
        if (buddyActionService.isAction(call.name)) {
            val outcome = buddyActionService.propose(call, userId)
            outcome.proposal?.let { proposal ->
                emit(
                    BuddyStreamEvent(
                        type = ACTION_PROPOSAL,
                        action = proposal.action,
                        label = proposal.label,
                        question = proposal.question,
                        taskId = proposal.taskId?.toString(),
                        // Every field the proposal carries must be copied through; a dropped one
                        // surfaces as the action politely refusing, not as an error.
                        title = proposal.title,
                        attesterId = proposal.attesterId,
                        githubLogin = proposal.githubLogin,
                        competencyKey = proposal.competencyKey,
                        level = proposal.level,
                        stepId = proposal.stepId?.toString(),
                        questionId = proposal.questionId?.toString(),
                        phaseId = proposal.phaseId?.toString(),
                        onboardingTaskId = proposal.onboardingTaskId?.toString(),
                        answer = proposal.answer,
                        optionIds = proposal.optionIds.takeIf { it.isNotEmpty() }?.map { it.toString() },
                        description = proposal.description,
                        reason = proposal.reason,
                        waitsOnIds = proposal.waitsOnIds.takeIf { it.isNotEmpty() }?.map { it.toString() },
                        unlocksIds = proposal.unlocksIds.takeIf { it.isNotEmpty() }?.map { it.toString() },
                        checklistTitle = proposal.checklistTitle,
                        checklistItems = proposal.checklistItems,
                        cardId = proposal.cardId?.toString(),
                        noteText = proposal.noteText,
                        lineBefore = proposal.lineBefore,
                        lineAfter = proposal.lineAfter,
                        linkUrl = proposal.linkUrl,
                        linkLabel = proposal.linkLabel,
                        cardIds = proposal.cardIds?.map { it.toString() },
                        cardNames = proposal.cardNames,
                        basedOn = proposal.basedOn,
                        preview = proposal.preview,
                    ),
                )
            }
            outcome.toolResult
        } else {
            emit(toolUseEvent(call.name))
            buddyToolExecutor.execute(call, userId, currentPage)
        }

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

    companion object {
        /**
         * How many backend-tool round trips a turn may take before it answers with what it has; the team
         * buddy's loop shares the budget.
         */
        const val MAX_AGENT_STEPS = 5

        /**
         * The most messages the AI is ever sent; older turns reach it only through the running summary.
         * Compaction folds back down to the same number, so the prompt stays within budget.
         */
        const val WINDOW = 20

        const val FALLBACK_REPLY =
            "I wasn't able to finish answering that one — could you rephrase or add a little detail?"

        /**
         * Shown when the hire is on no project: retrieval fails closed on an empty scope, so the turn is
         * refused rather than answered from nothing.
         */
        const val NO_PROJECT_REPLY =
            "I can't look through your project's material yet — you're not on a project, so " +
                "there's nothing for me to search. Once you're added to one, I'll be able to find " +
                "its docs, code and conventions for you. If you expected to be on a project " +
                "already, ask your project manager to add you."

        // The stream vocabulary the client switches on, plus one for the opening's suggested next
        // step. OPENING_ACTION is NOT `action_proposal`: that type is gated on the hire
        // confirming, whereas this only fills the composer with a question.
        const val TOKEN = "token"
        const val DONE = "done"

        /** Terminal like DONE: the turn failed after its stream had started. */
        const val ERROR = "error"

        /** Team mode only: the words shown so far were not the answer and are to be dropped. */
        const val RESET = "reset"
        const val OPENING_ACTION = "opening_action"

        // A change offered for the user's confirmation, and the notice that a tool is running.
        const val ACTION_PROPOSAL = "action_proposal"
        const val TOOL_USE = "tool_use"
        const val TOOL_KIND = "tool"

        /**
         * Splits after each space, keeping the space on the preceding chunk, so the concatenated tokens
         * reproduce the answer exactly.
         */
        val TOKEN_CHUNK = Regex("(?<= )")
    }

    private fun stripQuotedSelection(content: String): String =
        content
            .lineSequence()
            .filterNot { it.trimStart().startsWith(">") }
            .joinToString("\n")
            .trim()

    @Transactional
    @Tracked("Deleting message from session")
    fun deleteMessage(authId: String, messageId: UUID) {
        val userId = resolveUserId(authId)
        val message = buddyMessageRepository.findById(messageId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Message with id $messageId not found")
        }
        val session = message.session

        if (session.userId != userId) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Session containing message with id $messageId is not owned by the current user.",
            )
        }

        val messages = buddyMessageRepository
            .findAllBySessionIdOrderByCreatedAtAsc(session.id)

        val index = messages.indexOfFirst { it.id == message.id }

        if (index < session.summarizedCount) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Cannot delete a summarized message",
            )
        }
        buddyMessageRepository.delete(message)
    }

    @Transactional
    @Tracked("Binning session")
    fun binSession(authId: String, sessionId: UUID) {
        val userId = resolveUserId(authId)
        val session = buddySessionRepository.findByIdAndUserId(sessionId, userId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Session with id $sessionId not found for current user",
            )
        session.status = BuddySessionStatus.BINNED
        session.binnedAt = clock.instant()
        buddySessionRepository.save(session)
    }
}

internal fun parseUuidOrNull(value: String): UUID? =
    try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
    }
