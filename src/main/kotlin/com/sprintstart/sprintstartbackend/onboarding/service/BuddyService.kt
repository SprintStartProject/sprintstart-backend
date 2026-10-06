package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.client.BuddyAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.external.event.QuestionAskedEvent
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyAgentRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCitationDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyOpenStreamEvent
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
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
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
import com.sprintstart.sprintstartbackend.user.external.UserApi
import com.sprintstart.sprintstartbackend.user.service.SessionActivityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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
// One method per thing a visit can do -- open it, stream it open, read it, speak into it -- plus the
// agent loop's helpers. The count tracks the conversation's surface, not a class doing two jobs.
@Suppress("TooManyFunctions")
class BuddyService(
    private val buddySessionRepository: BuddySessionRepository,
    private val buddyMessageRepository: BuddyMessageRepository,
    private val buddyCitationRepository: BuddyCitationRepository,
    private val onboardingAiClient: OnboardingAiClient,
    private val buddyToolExecutor: BuddyToolExecutor,
    private val buddyActionService: BuddyActionService,
    private val userApi: UserApi,
    private val buddyCompactionService: BuddyCompactionService,
    private val artifactLookupService: ArtifactLookupService,
    private val sessionActivityService: SessionActivityService,
    private val applicationScope: CoroutineScope,
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
        val messages = buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
        // No marker anywhere falls back to showing *everything*, never nothing.
        return messages
            .drop(messages.indexOfLast { it.opening }.takeIf { it >= 0 } ?: 0)
            .map { it.toResponse() }
    }

    /**
     * Creates a new session for the current user.
     *
     * @param authId The ID of the currently authenticated user.
     * @param projectId The (optional) ID of the project the chat is linked to.
     */
    fun createSession(authId: String, projectId: UUID?): CreateSessionResponse {
        val userId = resolveUserId(authId)
        val session = BuddySession(
            userId = userId,
            projectId = projectId,
        )
        buddySessionRepository.save(session)
        return CreateSessionResponse(session.id)
    }

    /**
     * Opens a visit, streaming the greeting as the mentor writes it.
     *
     * The greeting is persisted as the visit's opening message. No transcript is replayed.
     *
     * A broken stream is two rules, not one:
     * - Nothing arrived — the fallback greeting is emitted and nothing is persisted, so a
     *   reload tries the model again.
     * - Some arrived — what the hire already read is persisted, so a reload shows the same
     *   words. Memory and cursor are untouched either way.
     *
     * @throws ResponseStatusException 404 if the authenticated user doesn't exist.
     */
    suspend fun streamOpenForMe(
        authId: String,
        sessionId: UUID?,
    ): Flow<BuddyStreamEvent> {
        val userId = resolveUserId(authId)

        if (sessionId == null) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "sessionId required",
            )
        }

        val session = buddySessionRepository.findByIdAndUserId(sessionId, userId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Session not found for current user",
            )

        val messages = buddyMessageRepository
            .findAllBySessionIdOrderByCreatedAtAsc(session.id)

        /*
         * The current request is counted as activity, but the decision whether
         * this is a long absence is made from the previous lastSeenAt.
         */
        val longAbsence = sessionActivityService.recordActivityAndReturnLongAbsence(userId)

        val firstConversation = buddySessionRepository.countByUserId(userId) == 1L

        /*
         * The last opening is special: opening the same conversation again without
         * the hire saying anything replays the existing opening instead of asking
         * the AI for another greeting.
         */
        if (!longAbsence) {
            messages
                .lastOrNull()
                ?.takeIf { it.opening }
                ?.let { opening ->
                    return flow {
                        emit(
                            BuddyStreamEvent(
                                type = TOKEN,
                                content = opening.content,
                            ),
                        )
                        emit(BuddyStreamEvent(type = DONE))
                    }
                }
        }

        if (!firstConversation && !longAbsence) {
            return flowOf(BuddyStreamEvent(type = DONE))
        }

        val recent = messages
            .drop(session.summarizedCount)
            .map { it.toAgentMessage() }

        val state = buddyToolExecutor.stateSnapshot(userId)

        return flow {
            val streamed = StringBuilder()
            var opening: BuddyOpenStreamEvent? = null

            try {
                onboardingAiClient
                    .streamBuddyOpen(
                        BuddyOpenRequest(
                            memory = session.summary,
                            recent = recent,
                            state = state,
                        ),
                    ).collect { event ->
                        when (event.type) {
                            TOKEN -> {
                                streamed.append(event.content.orEmpty())

                                emit(
                                    BuddyStreamEvent(
                                        type = TOKEN,
                                        content = event.content,
                                    ),
                                )
                            }

                            DONE -> {
                                opening = event
                            }
                        }
                    }
            } catch (e: OnboardingAiException) {
                logger.warn(
                    "Buddy opening failed for session {}",
                    session.id,
                    e,
                )
            }

            finishOpen(
                session = session,
                streamed = streamed.toString(),
                opening = opening,
            )

            compactInBackground(userId, session.id)
        }
    }

    /**
     * Folds this session's backlog into the mentor's memory without anybody waiting on it.
     *
     * Fire-and-forget on the application scope, matching `CorpusIndexedListener`: the fold is a
     * prompt-shaping device, so one that dies costs a longer prompt on the next turn and nothing
     * else. The point of the whole change is that no hire is ever blocked on this, so it must
     * not be awaited here, and [BuddyCompactionService.compactIfNeeded] never throws.
     */
    private fun compactInBackground(userId: UUID, sessionId: UUID) {
        applicationScope.launch {
            buddyCompactionService.compactIfNeeded(userId, sessionId)
        }
    }

    /**
     * Persists what the open produced and emits the terminal events.
     *
     * Three outcomes differing in what each may write: a complete open stores the greeting, a
     * broken one stores only the words the hire saw, and one that produced nothing writes nothing.
     *
     * Nothing here touches the memory or the cursor — that is [BuddyCompactionService]'s.
     */
    private suspend fun FlowCollector<BuddyStreamEvent>.finishOpen(
        session: BuddySession,
        streamed: String,
        opening: BuddyOpenStreamEvent?,
    ) {
        val greeting = opening?.greeting?.takeIf { it.isNotBlank() } ?: streamed

        if (greeting.isBlank()) {
            // Nothing reached the hire, so nothing is persisted: storing the fallback would make
            // an outage this visit's permanent greeting.
            emit(BuddyStreamEvent(type = TOKEN, content = FALLBACK_OPENING))
            emit(BuddyStreamEvent(type = DONE))
            return
        }

        buddyMessageRepository.save(
            BuddyMessage(
                session = session,
                role = BuddyMessageRole.ASSISTANT,
                content = greeting,
                // This is the first message of the conversation.
                opening = true,
            ),
        )

        opening?.action?.let {
            emit(BuddyStreamEvent(type = OPENING_ACTION, label = it.label, question = it.question))
        }
        emit(BuddyStreamEvent(type = DONE))
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
     * Per message rather than per session. A hire who looked something up and then wants the mentor
     * back should not have to remember which state a switch was left in, and the transcript stays
     * one conversation across the change.
     *
     * The user's message is persisted immediately; the assistant's reply is persisted only once the
     * agent loop finishes, so a stream that errors or is cancelled leaves no garbage reply behind.
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

        // Check if title has to be generated
        if (session.title.isBlank()) {
            try {
                val generatedTitle = buddyAiClient.getSessionTitle(AiGenerateSessionTitleRequest(content))
                session.title = generatedTitle.title
                buddySessionRepository.save(session)
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
                    chatId = session.id,
                    projectId = projectId,
                    question = questionForFaq,
                    askedAt = message.createdAt,
                ),
            )
        }

        // The AI reasoner sees the read-only tools *and* the action tools it may propose. An action
        // tool call never mutates here — it produces a proposal the hire must confirm out-of-band.
        //
        // With capabilities off the list is empty, which is the whole mechanism: a tool the model
        // was never given is one it cannot call, so the mode is enforced here rather than asked for
        // in the prompt. Retrieval is untouched — `search_docs` runs AI-side, not as a backend tool.
        val tools = if (capabilitiesEnabled) {
            buddyToolExecutor.toolSpecs(userId) + buddyActionService.actionSpecs(userId)
        } else {
            emptyList()
        }

        // Both resolved once per turn, not per hop: neither can change mid-conversation, and
        // re-reading would cost a membership lookup on every step of the agent loop.
        val vocabulary = vocabulary()
        val projectIds = session.projectId?.let { listOf(it.toString()) } ?: projectIdsFor(userId)

        // A hire on no project has no scope the AI may retrieve from — it fails closed on an empty
        // list — so a turn would search nothing and answer as though the project had no material on
        // the subject. Refuse the turn here instead, and say the one thing that resolves it. The
        // user's message is already persisted above, so the transcript still shows what they asked.
        if (projectIds.isEmpty()) {
            return flow {
                emitAgentReply(NO_PROJECT_REPLY, emptyList(), emptyList(), StringBuilder())
                buddyMessageRepository.save(
                    BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = NO_PROJECT_REPLY),
                )
            }
        }

        return flow {
            var messages = history + BuddyAgentMessageDto(role = "user", content = content)
            val reasoning = mutableListOf<String>()
            val citations = mutableListOf<BuddyCitationDto>()
            var answer: String? = null
            var step = 0

            while (answer == null && step < MAX_AGENT_STEPS) {
                step++
                val response = onboardingAiClient.buddyAgentTurn(
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
                )
                reasoning += response.reasoning
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
                                role = "tool",
                                content = runToolCall(call, userId, currentPage),
                                toolCallId = call.id,
                            ),
                        )
                    }
                    messages = next
                }
            }

            val reply = answer?.takeIf { it.isNotBlank() } ?: FALLBACK_REPLY

            val resolvedCitations = citations.mapNotNull { citation ->
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

            val emittedContent = StringBuilder()

            try {
                emitAgentReply(reply, reasoning, resolvedCitations, emittedContent)
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
                    content = reply,
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
            // Only now, with the reply persisted and the hire reading it. Folding before this point
            // is what the whole change exists to stop.
            compactInBackground(userId, session.id)
        }
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
                        type = "action_proposal",
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
                    ),
                )
            }
            outcome.toolResult
        } else {
            emit(BuddyStreamEvent(type = "tool_use", name = call.name, kind = "tool"))
            buddyToolExecutor.execute(call, userId, currentPage)
        }

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

    companion object {
        // How many agent round-trips (AI reason -> backend tool -> AI reason) before we stop and
        // answer with what we have. The AI service has its own internal search budget; this bounds
        // only the backend-tool hops so a loop can never run unbounded.
        //
        // Visible to BuddyTeamService, whose loop is bounded by the same budget.
        const val MAX_AGENT_STEPS = 5

        // The most messages (user + assistant) the AI is ever sent; older turns reach it only
        // through the session's running summary. 20 keeps ~10 exchanges verbatim.
        //
        // Visible to BuddyCompactionService, which folds back down to it. One constant, not two:
        // a fold target disagreeing with the window it feeds leaves the prompt over budget.
        const val WINDOW = 20

        const val FALLBACK_REPLY =
            "I wasn't able to finish answering that one — could you rephrase or add a little detail?"

        // Shown when the hire is on no project. Retrieval fails closed on an empty scope, so a
        // turn would search nothing and answer as though the project had no material — the worst
        // possible moment to sound confident. The turn is refused instead, and the hire is told the
        // state and the one thing that resolves it, the same way `BuddyBoardTools` tells them there
        // is no board to put a card on.
        const val NO_PROJECT_REPLY =
            "I can't look through your project's material yet — you're not on a project, so " +
                "there's nothing for me to search. Once you're added to one, I'll be able to find " +
                "its docs, code and conventions for you. If you expected to be on a project " +
                "already, ask your project manager to add you."

        // Shown when opening a visit can't reach the AI: a plain, warm welcome so the page still
        // works and the hire can start talking.
        const val FALLBACK_OPENING =
            "Welcome back! How can I help with your onboarding today?"

        // The stream vocabulary the client switches on, plus one for the opening's suggested next
        // step. OPENING_ACTION is NOT `action_proposal`: that type is gated on the hire
        // confirming, whereas this only fills the composer with a question.
        const val TOKEN = "token"
        const val DONE = "done"
        const val OPENING_ACTION = "opening_action"

        // Split after each space, keeping the space on the preceding chunk, so concatenating every
        // emitted token reproduces the answer exactly (newlines and punctuation preserved).
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
