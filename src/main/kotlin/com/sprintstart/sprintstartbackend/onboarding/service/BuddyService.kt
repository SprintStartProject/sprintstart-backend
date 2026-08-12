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
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyToolSpecDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyVocabularyDto
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.OnboardingAiException
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toAgentMessage
import com.sprintstart.sprintstartbackend.onboarding.model.mapper.toResponse
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.BuddyMessageResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
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
 * Manages a hire's ongoing onboarding buddy conversation: one continuous [BuddySession] per user,
 * durable across visits, backed by the stateless AI buddy-agent endpoint.
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
    private val onboardingAiClient: OnboardingAiClient,
    private val buddyToolExecutor: BuddyToolExecutor,
    private val buddyActionService: BuddyActionService,
    private val userApi: UserApi,
    private val trackService: TrackService,
    private val buddyCompactionService: BuddyCompactionService,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** Finds or creates a user's one ongoing buddy session. */
    fun getOrCreateSession(userId: UUID): BuddySession =
        buddySessionRepository.findByUserId(userId)
            ?: buddySessionRepository.save(BuddySession(userId = userId))

    /**
     * Returns the current visit's buddy messages, oldest first.
     *
     * From this visit's opening greeting onward. A visit opens fresh ([streamOpenForMe]); the
     * durable memory, not a transcript, carries continuity across visits.
     *
     * ⚠️ **The boundary is the last opening marker, never [BuddySession.summarizedCount].**
     * Keying it to the compaction cursor makes a hire's own scrollback shrink as the model folds.
     */
    fun getMessagesForMe(authId: String): List<BuddyMessageResponse> {
        val userId = resolveUserId(authId)
        val session = buddySessionRepository.findByUserId(userId) ?: return emptyList()
        val messages = buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
        // ⚠️ No marker anywhere falls back to showing *everything*, never nothing.
        return messages
            .drop(messages.indexOfLast { it.opening }.takeIf { it >= 0 } ?: 0)
            .map { it.toResponse() }
    }

    /**
     * Opens a visit, streaming the greeting as the mentor writes it.
     *
     * The greeting is persisted as the visit's opening message. No transcript is replayed.
     *
     * ⚠️ **A broken stream is two rules, not one:**
     * - **Nothing arrived** — the fallback greeting is emitted and **nothing is persisted**, so a
     *   reload tries the model again.
     * - **Some arrived** — what the hire already read is persisted, so a reload shows the same
     *   words. Memory and cursor are untouched either way.
     *
     * @throws ResponseStatusException 404 if the authenticated user doesn't exist.
     */
    suspend fun streamOpenForMe(authId: String): Flow<BuddyStreamEvent> {
        val userId = resolveUserId(authId)
        val session = getOrCreateSession(userId)

        val all = buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)

        // ⚠️ Opening twice without the hire saying anything is the same visit: a visit ends when
        // the hire speaks, so the greeting already there is replayed whole.
        //
        // ⚠️ The test is *the last message is an opening*, never *an opening exists* -- the hire
        // speaking is exactly what puts something after it.
        val greetingAlreadyThere = all.lastOrNull()?.takeIf { it.opening }
        if (greetingAlreadyThere != null) {
            return flowOf(
                BuddyStreamEvent(type = TOKEN, content = greetingAlreadyThere.content),
                BuddyStreamEvent(type = DONE),
            )
        }

        // Everything the memory note does not yet cover, so the greeting can be specific about a
        // previous visit. Still the compaction cursor -- this is a prompt, which is the one
        // question that cursor really answers.
        val recent = all.drop(session.summarizedCount).map { it.toAgentMessage() }
        val state = buddyToolExecutor.stateSnapshot(userId)

        return flow {
            val streamed = StringBuilder()
            var opening: BuddyOpenStreamEvent? = null
            try {
                onboardingAiClient
                    .streamBuddyOpen(
                        BuddyOpenRequest(memory = session.summary, recent = recent, state = state),
                    ).collect { event ->
                        when (event.type) {
                            BuddyOpenStreamEvent.TOKEN -> event.content?.let {
                                streamed.append(it)
                                emit(BuddyStreamEvent(type = TOKEN, content = it))
                            }

                            BuddyOpenStreamEvent.DONE -> opening = event
                        }
                    }
            } catch (@Suppress("SwallowedException") e: OnboardingAiException) {
                // Opening the buddy must never fail the page.
                logger.warn("Buddy open stream failed: {}", e.message)
            }

            finishOpen(session, streamed.toString(), opening)
            // Whatever the previous visit left unfolded gets folded now, while the hire is reading
            // the greeting rather than waiting on it.
            compactInBackground(userId)
        }
    }

    /**
     * Folds this session's backlog into the mentor's memory without anybody waiting on it.
     *
     * Fire-and-forget on the application scope, matching `CorpusIndexedListener`: the fold is a
     * prompt-shaping device, so one that dies costs a longer prompt on the next turn and nothing
     * else. ⚠️ The point of the whole change is that **no hire is ever blocked on this**, so it must
     * not be awaited here, and [BuddyCompactionService.compactIfNeeded] never throws.
     */
    private fun compactInBackground(userId: UUID) {
        applicationScope.launch {
            buddyCompactionService.compactIfNeeded(userId)
        }
    }

    /**
     * Persists what the open produced and emits the terminal events.
     *
     * Three outcomes differing in what each may write: a complete open stores the greeting, a
     * broken one stores only the words the hire saw, and one that produced nothing writes nothing.
     *
     * ⚠️ **Nothing here touches the memory or the cursor** — that is [BuddyCompactionService]'s.
     */
    private suspend fun FlowCollector<BuddyStreamEvent>.finishOpen(
        session: BuddySession,
        streamed: String,
        opening: BuddyOpenStreamEvent?,
    ) {
        val greeting = opening?.greeting?.takeIf { it.isNotBlank() } ?: streamed

        if (greeting.isBlank()) {
            // ⚠️ Nothing reached the hire, so nothing is persisted: storing the fallback would make
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
                // Marks the visit boundary, for the replay check above and the hire's transcript.
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
     * The user's message is persisted immediately; the assistant's reply is persisted only once the
     * agent loop finishes, so a stream that errors or is cancelled leaves no garbage reply behind.
     *
     * The AI never receives the whole transcript: only the window after the session's
     * [BuddySession.summarizedCount] cursor, plus the running summary standing in for the rest.
     *
     * ⚠️ **This turn must never ask the AI to fold.** A fold runs before the reply is composed, and
     * since the cursor advances by exactly what it folds, the window would then sit at [WINDOW]
     * permanently — an extra serialized model call on every turn. [BuddyCompactionService] folds
     * afterwards instead; a fold that has not happened yet just means a longer window this turn.
     *
     * @throws ResponseStatusException 404 if the authenticated user doesn't exist.
     */
    suspend fun sendMessageForMe(authId: String, content: String): Flow<BuddyStreamEvent> {
        val userId = resolveUserId(authId)
        val session = getOrCreateSession(userId)

        // Read history before saving the new message so it isn't sent to the AI service twice.
        // Everything the summary already covers stays out of the prompt.
        val history = buddyMessageRepository
            .findAllBySessionIdOrderByCreatedAtAsc(session.id)
            .drop(session.summarizedCount)
            .map { it.toAgentMessage() }

        buddyMessageRepository.save(
            BuddyMessage(session = session, role = BuddyMessageRole.USER, content = content),
        )

        // The AI reasoner sees the read-only tools *and* the action tools it may propose. An action
        // tool call never mutates here — it produces a proposal the hire must confirm out-of-band.
        val tools = buddyToolExecutor.toolSpecs(userId) + buddyActionService.actionSpecs()

        // Both resolved once per turn, not per hop: neither can change mid-conversation, and
        // re-reading would cost a membership lookup on every step of the agent loop.
        val vocabulary = vocabularyFor(userId)
        val projectIds = projectIdsFor(userId)

        return flow {
            var messages = history + BuddyAgentMessageDto(role = "user", content = content)
            var citations: List<BuddyCitationDto> = emptyList()
            var answer: String? = null
            var step = 0

            while (answer == null && step < MAX_AGENT_STEPS) {
                step++
                val response = onboardingAiClient.buddyAgentTurn(
                    agentRequest(messages, tools, step, session, vocabulary, projectIds),
                )
                citations = response.citations
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
                                content = runToolCall(call, userId),
                                toolCallId = call.id,
                            ),
                        )
                    }
                    messages = next
                }
            }

            val reply = answer?.takeIf { it.isNotBlank() } ?: FALLBACK_REPLY
            // The agent turn returns the answer whole; emit it in word-sized chunks so the client
            // still renders it progressively. This is paced emission, not true token streaming --
            // streaming the model's tokens through a tool-calling turn is a separate change.
            for (chunk in TOKEN_CHUNK.split(reply).filter { it.isNotEmpty() }) {
                emit(BuddyStreamEvent(type = "token", content = chunk))
            }
            for (citation in citations) {
                emit(
                    BuddyStreamEvent(
                        type = "citation",
                        artifactId = citation.artifactId,
                        startLine = citation.startLine,
                        startPage = citation.startPage,
                    ),
                )
            }
            emit(BuddyStreamEvent(type = "done"))

            buddyMessageRepository.save(
                BuddyMessage(session = session, role = BuddyMessageRole.ASSISTANT, content = reply),
            )
            // Only now, with the reply persisted and the hire reading it. Folding before this point
            // is what the whole change exists to stop.
            compactInBackground(userId)
        }
    }

    /** This hire's track vocabulary, in the shape the AI service's persona skeleton expects. */
    private fun vocabularyFor(userId: UUID): BuddyVocabularyDto {
        val track = trackService.forUser(userId)
        return BuddyVocabularyDto(
            contributionNoun = track.contributionNoun,
            contributionNounPlural = track.contributionNounPlural,
            contributionVerbPast = track.contributionVerbPast,
        )
    }

    /**
     * Builds one agent request. The summary goes on the first hop only: after that the AI has
     * folded it into the running conversation it returns, and re-sending would double-fold
     * messages already inside it.
     *
     * ⚠️ **Nothing here may ask the AI to fold**, and neither side carries a field for it: a fold
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
        )

    /**
     * The projects whose material this hire may be shown, as ids.
     *
     * Every project they are on rather than one of them: the buddy is not a per-project surface,
     * and a hire onboarding on two projects asking "how do we deploy" means either. An empty list
     * means the AI searches everything, which is the honest answer for somebody on no project yet
     * — there is nothing narrower that would be true.
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
                        moduleId = proposal.moduleId?.toString(),
                        answer = proposal.answer,
                        // ⚠️ Every confirm payload the proposal carries, without exception. title
                        // and attesterId were missing here, and the omission was invisible: the
                        // confirm handles them being null by returning a polite refusal, so
                        // ⚠️ Every field the proposal carries must be copied through; a dropped one
                        // surfaces as the action politely refusing, not as an error.
                        title = proposal.title,
                        attesterId = proposal.attesterId,
                        githubLogin = proposal.githubLogin,
                    ),
                )
            }
            outcome.toolResult
        } else {
            emit(BuddyStreamEvent(type = "tool_use", name = call.name, kind = "tool"))
            buddyToolExecutor.execute(call, userId)
        }

    private fun resolveUserId(authId: String): UUID =
        userApi
            .getUserIdByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: $authId") }

    companion object {
        // How many agent round-trips (AI reason -> backend tool -> AI reason) before we stop and
        // answer with what we have. The AI service has its own internal search budget; this bounds
        // only the backend-tool hops so a loop can never run unbounded.
        private const val MAX_AGENT_STEPS = 5

        // The most messages (user + assistant) the AI is ever sent; older turns reach it only
        // through the session's running summary. 20 keeps ~10 exchanges verbatim.
        //
        // ⚠️ Visible to BuddyCompactionService, which folds back down to it. One constant, not two:
        // a fold target disagreeing with the window it feeds leaves the prompt over budget.
        const val WINDOW = 20

        const val FALLBACK_REPLY =
            "I wasn't able to finish answering that one — could you rephrase or add a little detail?"

        // Shown when opening a visit can't reach the AI: a plain, warm welcome so the page still
        // works and the hire can start talking.
        const val FALLBACK_OPENING =
            "Welcome back! How can I help with your onboarding today?"

        // The stream vocabulary the client switches on, plus one for the opening's suggested next
        // step. ⚠️ OPENING_ACTION is NOT `action_proposal`: that type is gated on the hire
        // confirming, whereas this only fills the composer with a question.
        const val TOKEN = "token"
        const val DONE = "done"
        const val OPENING_ACTION = "opening_action"

        // Split after each space, keeping the space on the preceding chunk, so concatenating every
        // emitted token reproduces the answer exactly (newlines and punctuation preserved).
        val TOKEN_CHUNK = Regex("(?<= )")
    }
}
