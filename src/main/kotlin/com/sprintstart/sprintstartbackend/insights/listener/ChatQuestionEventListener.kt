package com.sprintstart.sprintstartbackend.insights.listener

import com.sprintstart.sprintstartbackend.chat.external.events.ChatQuestionAskedEvent
import com.sprintstart.sprintstartbackend.insights.service.FaqLiveUpdateService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Updates the FAQ insight whenever someone asks the AI Buddy a question.
 *
 * The handoff to [applicationScope] is the point of this class. A plain `@EventListener` runs on
 * the publisher's thread, and the publisher here is the chat prompt — so doing the work inline
 * would make every user wait for an AI classification round-trip before their answer starts
 * streaming. The FAQ is allowed to lag by a second; the chat is not.
 *
 * For the same reason failures are logged and dropped: a question that could not be filed is a
 * missing FAQ entry until the next manual refresh, which is not worth failing anything else over.
 */
@Component
class ChatQuestionEventListener(
    private val faqLiveUpdateService: FaqLiveUpdateService,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @EventListener
    fun handleQuestionAsked(event: ChatQuestionAskedEvent) {
        applicationScope.launch {
            try {
                faqLiveUpdateService.onQuestionAsked(event)
            } catch (e: Exception) {
                logger.warn(
                    "Could not file question from message {} into the FAQ of project {}",
                    event.messageId,
                    event.projectId,
                    e,
                )
            }
        }
    }
}
