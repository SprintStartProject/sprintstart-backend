package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyCitationDto
import com.sprintstart.sprintstartbackend.onboarding.external.model.BuddyStreamEvent
import kotlinx.coroutines.flow.FlowCollector

/**
 * Emits a finished agent answer on the buddy stream: its words, its citations, then `done`.
 *
 * Shared by the hire's buddy and team mode so both streams speak the one vocabulary the client
 * switches on. The agent turn returns the answer whole, so it is emitted in word-sized chunks and
 * the client still renders it progressively. This is paced emission, not true token streaming --
 * streaming the model's tokens through a tool-calling turn is a separate change.
 */
internal suspend fun FlowCollector<BuddyStreamEvent>.emitAgentReply(
    reply: String,
    citations: List<BuddyCitationDto>,
) {
    for (chunk in BuddyService.TOKEN_CHUNK.split(reply).filter { it.isNotEmpty() }) {
        emit(BuddyStreamEvent(type = BuddyService.TOKEN, content = chunk))
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
    emit(BuddyStreamEvent(type = BuddyService.DONE))
}
