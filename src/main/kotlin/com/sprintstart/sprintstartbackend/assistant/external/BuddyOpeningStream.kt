package com.sprintstart.sprintstartbackend.assistant.external

import com.sprintstart.sprintstartbackend.assistant.external.AgentAiClient
import com.sprintstart.sprintstartbackend.assistant.external.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.assistant.external.BuddyOpenStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEventType
import com.sprintstart.sprintstartbackend.shared.ai.AiServiceException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * How one buddy conversation stores its opening and what follows it; the hire's and the team's differ only here.
 *
 * @property fallbackGreeting shown, never stored, when the model produced nothing.
 * @property saveOpening stores the greeting the reader saw as the visit's opening message.
 * @property afterOpening runs once the opening is settled, whatever the outcome (the memory fold).
 */
class OpeningConversation(
    val fallbackGreeting: String,
    val saveOpening: (String) -> Unit,
    val afterOpening: () -> Unit,
)

/**
 * Opens a buddy visit: the protocol the hire's buddy and the team buddy share.
 *
 * The greeting streams to the reader as the model writes it. What the reader saw is what gets stored:
 * a complete opening stores the model's greeting, a stream that broke part-way stores the words that
 * arrived, and an opening that produced nothing stores nothing and shows the fallback — storing the
 * fallback would make an outage the visit's permanent greeting.
 */
@Component
class BuddyOpeningStream(
    private val agentAiClient: AgentAiClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** The visit's existing greeting, sent again whole instead of asking the model for another. */
    fun replay(greeting: String): Flow<BuddyStreamEvent> =
        flowOf(
            BuddyStreamEvent(type = BuddyStreamEventType.TOKEN, content = greeting),
            BuddyStreamEvent(type = BuddyStreamEventType.DONE),
        )

    /** Streams a new greeting for [request] and settles it in [conversation]. */
    fun open(
        request: BuddyOpenRequest,
        conversation: OpeningConversation,
    ): Flow<BuddyStreamEvent> =
        flow {
            val relayed = relay(request)
            settle(relayed, conversation)
            conversation.afterOpening()
        }

    /** Forwards each token as it arrives; returns what was shown and the closing event, if one came. */
    private suspend fun FlowCollector<BuddyStreamEvent>.relay(request: BuddyOpenRequest): RelayedOpening {
        val shown = StringBuilder()
        var closing: BuddyOpenStreamEvent? = null
        try {
            agentAiClient.streamBuddyOpen(request).collect { event ->
                when (event.type) {
                    BuddyOpenStreamEvent.TOKEN -> event.content?.let {
                        shown.append(it)
                        emit(BuddyStreamEvent(type = BuddyStreamEventType.TOKEN, content = it))
                    }

                    BuddyOpenStreamEvent.DONE -> closing = event
                }
            }
        } catch (e: AiServiceException) {
            logger.warn("Buddy opening stream failed; keeping the {} characters already shown", shown.length, e)
        }
        return RelayedOpening(shown.toString(), closing)
    }

    private suspend fun FlowCollector<BuddyStreamEvent>.settle(
        relayed: RelayedOpening,
        conversation: OpeningConversation,
    ) {
        val greeting = relayed.closing?.greeting?.takeIf { it.isNotBlank() } ?: relayed.shown
        if (greeting.isBlank()) {
            emit(BuddyStreamEvent(type = BuddyStreamEventType.TOKEN, content = conversation.fallbackGreeting))
            emit(BuddyStreamEvent(type = BuddyStreamEventType.DONE))
            return
        }
        conversation.saveOpening(greeting)
        relayed.closing?.action?.let {
            emit(BuddyStreamEvent(type = BuddyStreamEventType.OPENING_ACTION, label = it.label, question = it.question))
        }
        emit(BuddyStreamEvent(type = BuddyStreamEventType.DONE))
    }

    private class RelayedOpening(
        val shown: String,
        val closing: BuddyOpenStreamEvent?,
    )
}

/** The messages of the current visit: everything from the last opening on, or all of them if none was opened. */
internal fun <T> List<T>.sinceLastOpening(isOpening: (T) -> Boolean): List<T> = drop(
    indexOfLast(isOpening).coerceAtLeast(0),
)
