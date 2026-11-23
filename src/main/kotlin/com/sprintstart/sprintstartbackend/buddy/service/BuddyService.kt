package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.AgentAiClient
import com.sprintstart.sprintstartbackend.assistant.external.ArtifactLookupService
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentMessageDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.assistant.external.BuddyTurnOptions
import com.sprintstart.sprintstartbackend.assistant.external.CONVERSATION_WINDOW
import com.sprintstart.sprintstartbackend.assistant.external.emitAgentReply
import com.sprintstart.sprintstartbackend.assistant.external.endFailureWithErrorEvent
import com.sprintstart.sprintstartbackend.assistant.external.sinceLastOpening
import com.sprintstart.sprintstartbackend.buddy.client.BuddyAiClient
import com.sprintstart.sprintstartbackend.buddy.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySessionStatus
import com.sprintstart.sprintstartbackend.buddy.model.exceptions.AiResponseException
import com.sprintstart.sprintstartbackend.buddy.model.mapper.toAgentMessage
import com.sprintstart.sprintstartbackend.buddy.model.mapper.toNewQuestion
import com.sprintstart.sprintstartbackend.buddy.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.buddy.model.request.buddy.AiGenerateSessionTitleRequest
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.CreateSessionResponse
import com.sprintstart.sprintstartbackend.buddy.model.response.buddy.GetSessionsResponse
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyCitationRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.knowledge.external.QuestionLogApi
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.failure.afterFailure
import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import com.sprintstart.sprintstartbackend.shared.ids.QuestionId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Clock
import java.util.UUID

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
    private val agentAiClient: AgentAiClient,
    private val buddyToolExecutor: BuddyToolExecutor,
    private val buddyActionService: BuddyActionService,
    private val userApi: UserApi,
    private val artifactLookupService: ArtifactLookupService,
    private val compactionScheduler: BuddyCompactionScheduler,
    private val buddyAiClient: BuddyAiClient,
    private val questionLog: QuestionLogApi,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val projectScope = BuddyProjectScope(userApi)
    private val replyStore = BuddyReplyStore(buddyMessageRepository, buddyCitationRepository, artifactLookupService)
    private val toolCalls = BuddyToolCallRunner(buddyToolExecutor, buddyActionService)
    private val agentLoop = BuddyAgentLoop(agentAiClient, toolCalls, replyStore)

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
            projectId = projectId ?: projectScope.singleProjectOf(projectScope.projectIdsFor(UserId(userId))),
        )
        buddySessionRepository.save(session)
        return CreateSessionResponse(session.id)
    }

    /**
     * Sends the authenticated user's message to the buddy and streams the reply.
     *
     * With [BuddyTurnOptions.capabilitiesEnabled] false the mentor answers from the project's material and does
     * nothing else: no tools are mounted, so none can be called, and the mode is enforced by what
     * the model was given rather than by what the prompt asked of it. Retrieval is unaffected —
     * `search_docs` runs AI-side — so this costs the hire nothing in what they can find.
     *
     * A hire on no project is refused rather than answered. Retrieval is scoped to their projects
     * and the AI admits nothing from an empty scope, so the turn would search nothing and reply as
     * though the project had no material — the worst moment to sound confident and the hardest
     * state for the hire to diagnose. They are told that instead, and what resolves it. See
     * [BuddyProjectScope.projectIdsFor].
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
     * since the cursor advances by exactly what it folds, the window would then sit at [CONVERSATION_WINDOW]
     * permanently — an extra serialized model call on every turn. [BuddyCompactionService] folds
     * afterward instead; a fold that has not happened yet just means a longer window this turn.
     *
     * @throws ResponseStatusException 404 if the authenticated user doesn't exist.
     */
    suspend fun sendMessageForMe(
        authId: String,
        sessionId: UUID?,
        content: String,
        options: BuddyTurnOptions = BuddyTurnOptions(),
    ): Flow<BuddyStreamEvent> {
        val userId = resolveUserId(authId)
        val user = UserId(userId)
        val session = ownedSession(sessionId, user)
        val memberProjects = rebindToMembership(session, user)
        nameIfUntitled(session, content)

        // Read history before saving the new message so it isn't sent to the AI service twice.
        // Everything the summary already covers stays out of the prompt.
        val history = buddyMessageRepository
            .findAllBySessionIdOrderByCreatedAtAsc(session.id)
            .drop(session.summarizedCount)
            .map { it.toAgentMessage() }
        recordQuestion(session, content)

        // Resolved once per turn, not per hop: neither can change mid-conversation, and
        // re-reading would cost a membership lookup on every step of the agent loop.
        val scope = BuddyTurnScope(
            tools = toolsFor(user, options.capabilitiesEnabled),
            vocabulary = buddyVocabulary(),
            projectIds = session.projectId?.let { listOf(it.toString()) } ?: memberProjects,
            capabilitiesEnabled = options.capabilitiesEnabled,
            filters = options.filters,
        )

        // A hire on no project has no retrieval scope, and the AI fails closed on an empty one, so the turn
        // is refused here with the one thing that resolves it. The message is already persisted.
        if (scope.projectIds.isEmpty()) {
            return refusalForNoProject(session)
        }
        return replyStream(BuddyTurn(session, user, content, options.currentPage, history, scope))
    }

    private fun ownedSession(requested: UUID?, owner: UserId): BuddySession {
        if (requested == null) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId required")
        }
        return buddySessionRepository.findByIdAndUserId(requested, owner.value) ?: throw ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Session not found for current user",
        )
    }

    /**
     * Resolved once per turn and reused for the retrieval scope. A session whose project the user has
     * left is rebound before the question event, so the event names the right one.
     */
    private fun rebindToMembership(session: BuddySession, user: UserId): List<String> {
        val memberProjects = projectScope.projectIdsFor(user)
        if (session.projectId != null && session.projectId.toString() !in memberProjects) {
            session.projectId = projectScope.singleProjectOf(memberProjects)
            buddySessionRepository.save(session)
        }
        return memberProjects
    }

    private suspend fun nameIfUntitled(session: BuddySession, content: String) {
        if (!session.title.isBlank()) return
        try {
            val generatedTitle = buddyAiClient.getSessionTitle(AiGenerateSessionTitleRequest(content))
            buddySessionRepository.nameIfUntitled(session.id, generatedTitle.title)
        } catch (e: AiResponseException) {
            logger.warn("Could not generate buddy session title", e)
        }
    }

    private fun recordQuestion(session: BuddySession, content: String) {
        val message = BuddyMessage(session = session, role = BuddyMessageRole.USER, content = content)
        buddyMessageRepository.save(message)
        session.projectId?.let { projectId ->
            questionLog.record(message.toNewQuestion(ProjectId(projectId), stripQuotedSelection(message.content)))
        }
    }

    /**
     * The reasoner gets the read-only tools and the action tools it may propose; an action never mutates
     * here. With capabilities off the list is empty, so the mode is enforced by what the model is given,
     * not asked for in the prompt.
     */
    private fun toolsFor(user: UserId, capabilitiesEnabled: Boolean): List<BuddyToolSpecDto> =
        if (capabilitiesEnabled) {
            buddyToolExecutor.toolSpecs(user.value) + buddyActionService.actionSpecs(user.value)
        } else {
            emptyList()
        }

    private fun refusalForNoProject(session: BuddySession): Flow<BuddyStreamEvent> = flow {
        emitAgentReply(NO_PROJECT_REPLY, emptyList(), StringBuilder())
        buddyMessageRepository.save(
            BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = NO_PROJECT_REPLY),
        )
    }

    private fun replyStream(turn: BuddyTurn): Flow<BuddyStreamEvent> = flow {
        // Everything the hire has been shown of the reply, across every hop of the loop.
        val emittedContent = StringBuilder()
        // The whole loop is guarded, not only the last emission: the words now leave while the
        // loop runs, so a stop or a failure at any hop leaves a partial reply worth keeping.
        val citations = afterFailure(cleanup = {
            replyStore.saveIncompleteReply(turn.session, emittedContent.toString())
        }) {
            with(agentLoop) { run(turn, emittedContent) }
        }

        replyStore.saveReply(turn.session, emittedContent.toString(), citations)
        // Only now, with the reply persisted: folding must never delay the answer.
        compactionScheduler.foldHireConversation(turn.user, BuddySessionId(turn.session.id))
    }.endFailureWithErrorEvent()

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

    companion object {
        /**
         * Shown when the hire is on no project: retrieval fails closed on an empty scope, so the turn is
         * refused rather than answered from nothing.
         */
        const val NO_PROJECT_REPLY =
            "I can't look through your project's material yet — you're not on a project, so " +
                "there's nothing for me to search. Once you're added to one, I'll be able to find " +
                "its docs, code and conventions for you. If you expected to be on a project " +
                "already, ask your project manager to add you."
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
        questionLog.forget(QuestionId(message.id))
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
