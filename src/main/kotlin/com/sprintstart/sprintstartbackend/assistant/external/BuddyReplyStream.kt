package com.sprintstart.sprintstartbackend.assistant.external

import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentResponse
import com.sprintstart.sprintstartbackend.assistant.external.BuddyAgentStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEventType
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException

/**
 * Relays one streamed AI agent call to the browser as it arrives, and returns how the call ended.
 *
 * Shared by the hire's buddy and team mode so both streams speak the one vocabulary the client
 * switches on. `reasoning` and `token` fragments and `tool_use` markers are passed on immediately;
 * the words are also appended to [emittedContent], which is what the reply is stored as and what a
 * cancelled turn keeps. The terminal `result` is not shown: it is returned, for the caller to either
 * finish on or run its pending backend tools.
 *
 * The AI separates the text of successive hops with a blank line, but only within one call. A call
 * that resumes after a backend tool starts with no memory of what an earlier call wrote, so the
 * separator at that seam is written here: the first token of a call is preceded by one when
 * [emittedContent] already holds text.
 *
 * @throws AiServiceException if the AI reports an `error`, or the stream ends without a `result`.
 */
internal suspend fun FlowCollector<BuddyStreamEvent>.relayAgentTurn(
    events: Flow<BuddyAgentStreamEvent>,
    emittedContent: StringBuilder,
): BuddyAgentResponse {
    var result: BuddyAgentResponse? = null
    var separatorOwed = emittedContent.isNotBlank()

    events.collect { event ->
        when (event.type) {
            BuddyAgentStreamEvent.REASONING -> event.content?.takeIf { it.isNotEmpty() }?.let {
                emit(BuddyStreamEvent(type = "reasoning", reasoning = it))
            }

            BuddyAgentStreamEvent.TOKEN -> event.content?.takeIf { it.isNotEmpty() }?.let {
                if (separatorOwed) {
                    emitToken(HOP_SEPARATOR, emittedContent)
                    separatorOwed = false
                }
                emitToken(it, emittedContent)
            }

            BuddyAgentStreamEvent.TOOL_USE ->
                emit(toolUseEvent(event.name))

            BuddyAgentStreamEvent.RESULT -> result = event.toResponse()

            BuddyAgentStreamEvent.ERROR -> throw AiServiceException(
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                event.message.orEmpty(),
                "The AI service failed the buddy turn: ${event.message}",
            )
        }
    }

    return result ?: throw AiServiceException(
        HttpStatus.BAD_GATEWAY.value(),
        "",
        "The AI service ended the buddy turn without a result.",
    )
}

/**
 * Ends a turn that failed part-way with an `error` event instead of a broken connection.
 *
 * By the time a hop fails, the status line and the first events have been sent, so an exception
 * thrown out of the flow can no longer become an HTTP error: the server drops the connection
 * without its closing chunk, and Vite's dev proxy keeps the browser's side of it open, so the reply
 * spins until the client's watchdog gives up. The cause is logged here; the client gets a fixed
 * sentence, because the upstream text is provider detail the reader cannot act on.
 *
 * A stop is rethrown untouched, since nobody is left to read an event. So is a failure after `done`
 * (persisting the reply or its citations): the reader already has the whole answer and has stopped
 * reading, and a second terminal event would contradict the first.
 */
internal fun Flow<BuddyStreamEvent>.endFailureWithErrorEvent(): Flow<BuddyStreamEvent> {
    val upstream = this
    return flow {
        var answered = false
        emitAll(
            upstream
                .onEach { if (it.type == BuddyStreamEventType.DONE) answered = true }
                .catch { e ->
                    if (e is CancellationException || answered) throw e
                    logger.error("Buddy turn failed after its stream started", e)
                    emit(BuddyStreamEvent(type = BuddyStreamEventType.ERROR, message = TURN_FAILED_MESSAGE))
                },
        )
    }
}

/**
 * Makes sure the reader is left with an answer after the agent loop.
 *
 * Normally everything has been streamed and there is nothing to add. If the loop ended without a
 * usable answer — the step budget ran out, or the final text was blank — the fallback is appended
 * after what was already shown. If the AI sent a final answer but no tokens, that answer is emitted
 * now, chunked, so the client still renders it progressively.
 */
internal suspend fun FlowCollector<BuddyStreamEvent>.completeReply(answer: String?, emittedContent: StringBuilder) {
    val missing = when {
        answer.isNullOrBlank() -> FALLBACK_REPLY
        emittedContent.isBlank() -> answer
        else -> return
    }
    if (emittedContent.isNotBlank()) {
        emitToken(HOP_SEPARATOR, emittedContent)
    }
    emitWords(missing, emittedContent)
}

/**
 * Emits a whole answer on the buddy stream: its words, its citations, then `done`.
 *
 * For replies that are known up front rather than streamed, such as the refusal for a hire on no
 * project. The words are emitted in word-sized chunks so the client renders it progressively.
 */
internal suspend fun FlowCollector<BuddyStreamEvent>.emitAgentReply(
    reply: String,
    citations: List<ResolvedBuddyCitation>,
    emittedContent: StringBuilder,
) {
    emitWords(reply, emittedContent)
    emitCitationsAndDone(citations)
}

/** Ends a reply on the buddy stream: the sources it drew on, then `done`. */
internal suspend fun FlowCollector<BuddyStreamEvent>.emitCitationsAndDone(citations: List<ResolvedBuddyCitation>) {
    for (citation in citations) {
        emit(
            BuddyStreamEvent(
                type = "citation",
                artifactId = citation.artifactId.toString(),
                filename = citation.filename,
                sourceUrl = citation.sourceUrl,
                startLine = citation.startLine,
                startPage = citation.startPage,
            ),
        )
    }
    emit(BuddyStreamEvent(type = BuddyStreamEventType.DONE))
}

private suspend fun FlowCollector<BuddyStreamEvent>.emitWords(text: String, emittedContent: StringBuilder) {
    for (chunk in TOKEN_CHUNK.split(text).filter { it.isNotEmpty() }) {
        emitToken(chunk, emittedContent)
    }
}

private suspend fun FlowCollector<BuddyStreamEvent>.emitToken(content: String, emittedContent: StringBuilder) {
    emit(BuddyStreamEvent(type = BuddyStreamEventType.TOKEN, content = content))
    emittedContent.append(content)
}

private const val HOP_SEPARATOR = "\n\n"

private const val TURN_FAILED_MESSAGE = "The buddy could not finish this reply."

private val logger = LoggerFactory.getLogger(
    "com.sprintstart.sprintstartbackend.teamspace.service.BuddyReplyStream",
)

/** The stream event that tells the client the buddy is running the tool [name]. */
internal fun toolUseEvent(name: String?): BuddyStreamEvent =
    BuddyStreamEvent(type = BuddyStreamEventType.TOOL_USE, name = name, kind = BuddyStreamEventType.TOOL_KIND)

internal data class ResolvedBuddyCitation(
    val artifactId: UUID,
    val filename: String,
    val sourceUrl: String?,
    val startLine: Int?,
    val startPage: Int?,
)

/** What a turn shows when it ends without an answer of its own. */
internal const val FALLBACK_REPLY =
    "I wasn't able to finish answering that one — could you rephrase or add a little detail?"

/**
 * Splits after each space, keeping the space on the preceding chunk, so the concatenated tokens
 * reproduce the answer exactly.
 */
private val TOKEN_CHUNK = Regex("(?<= )")
