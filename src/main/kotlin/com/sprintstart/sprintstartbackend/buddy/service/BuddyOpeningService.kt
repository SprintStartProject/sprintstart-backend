package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.assistant.external.BuddyOpenRequest
import com.sprintstart.sprintstartbackend.assistant.external.BuddyOpeningStream
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEvent
import com.sprintstart.sprintstartbackend.assistant.external.BuddyStreamEventType
import com.sprintstart.sprintstartbackend.assistant.external.OpeningConversation
import com.sprintstart.sprintstartbackend.buddy.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.buddy.model.mapper.toAgentMessage
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.shared.ids.AuthId
import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import com.sprintstart.sprintstartbackend.shared.ids.UserId
import com.sprintstart.sprintstartbackend.user.external.SessionActivityApi
import com.sprintstart.sprintstartbackend.user.external.UserApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

/**
 * Opens a visit to a hire's buddy conversation.
 *
 * The first conversation and a visit after a long absence get a new greeting, streamed as the model writes
 * it ([BuddyOpeningStream]). Otherwise an existing opening is replayed when it is the latest message, and a
 * conversation without a new opening is left untouched. The greeting is grounded in the hire's state, not in
 * a replayed transcript.
 */
@Service
class BuddyOpeningService(
    private val buddySessionRepository: BuddySessionRepository,
    private val buddyMessageRepository: BuddyMessageRepository,
    private val userApi: UserApi,
    private val sessionActivityService: SessionActivityApi,
    private val buddyToolExecutor: BuddyToolExecutor,
    private val openingStream: BuddyOpeningStream,
    private val compactionScheduler: BuddyCompactionScheduler,
) {
    /**
     * Opens [sessionId] for the signed-in hire.
     *
     * @throws ResponseStatusException 400 without a session, 404 for an unknown user or a session that is not theirs.
     */
    fun streamOpenForMe(authId: AuthId, sessionId: BuddySessionId?): Flow<BuddyStreamEvent> {
        val user = resolveUser(authId)
        val session = ownedSession(sessionId, user)
        val messages = buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(session.id)
        val longAbsence = sessionActivityService.recordVisit(user.value)
        val firstConversation = buddySessionRepository.countByUserId(user.value) == 1L
        val latestOpening = messages.lastOrNull()?.takeIf { it.opening }

        return when {
            !longAbsence && latestOpening != null -> openingStream.replay(latestOpening.content)
            !longAbsence && !firstConversation -> flowOf(BuddyStreamEvent(type = BuddyStreamEventType.DONE))
            else -> openingStream.open(openRequest(session, messages, user), conversationOf(session, user))
        }
    }

    private fun openRequest(session: BuddySession, messages: List<BuddyMessage>, user: UserId): BuddyOpenRequest =
        BuddyOpenRequest(
            memory = session.summary,
            recent = messages.drop(session.summarizedCount).map { it.toAgentMessage() },
            state = buddyToolExecutor.stateSnapshot(user.value),
        )

    private fun conversationOf(session: BuddySession, user: UserId): OpeningConversation =
        OpeningConversation(
            fallbackGreeting = FALLBACK_OPENING,
            saveOpening = { greeting ->
                buddyMessageRepository.save(
                    BuddyMessage(
                        session = session,
                        role = BuddyMessageRole.ASSISTANT,
                        content = greeting,
                        opening = true,
                    ),
                )
            },
            afterOpening = { compactionScheduler.foldHireConversation(user, BuddySessionId(session.id)) },
        )

    private fun resolveUser(authId: AuthId): UserId =
        userApi
            .getUserIdByAuthId(authId.value)
            .map(::UserId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with authId: ${authId.value}") }

    private fun ownedSession(sessionId: BuddySessionId?, user: UserId): BuddySession {
        if (sessionId == null) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId required")
        return buddySessionRepository.findByIdAndUserId(sessionId.value, user.value)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found for current user")
    }

    companion object {
        const val FALLBACK_OPENING = "Welcome back! How can I help with your onboarding today?"
    }
}
