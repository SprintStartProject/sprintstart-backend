package com.sprintstart.sprintstartbackend.buddy.service

import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.buddy.model.entity.BuddySessionStatus
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyCitationRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.buddy.repository.BuddySessionRepository
import com.sprintstart.sprintstartbackend.knowledge.external.QuestionLogApi
import com.sprintstart.sprintstartbackend.shared.ids.BuddySessionId
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.temporal.ChronoUnit

@Service
class BuddySessionCleanupService(
    private val sessionRepository: BuddySessionRepository,
    private val messageRepository: BuddyMessageRepository,
    private val citationRepository: BuddyCitationRepository,
    private val clock: Clock,
    private val questionLog: QuestionLogApi,
) {
    @Transactional
    fun deleteBinnedChats() {
        val cutoff = clock.instant().minus(7, ChronoUnit.DAYS)
        val sessions = sessionRepository.findByStatusAndBinnedAtBefore(BuddySessionStatus.BINNED, cutoff)
        sessions.forEach { session: BuddySession ->
            citationRepository.deleteAllByMessageSessionId(session.id)
            messageRepository.deleteAllBySessionId(session.id)
            questionLog.forgetConversation(BuddySessionId(session.id))
            sessionRepository.delete(session)
        }
    }
}
