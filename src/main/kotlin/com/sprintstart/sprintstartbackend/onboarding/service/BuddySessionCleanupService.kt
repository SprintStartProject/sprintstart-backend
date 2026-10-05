package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyCitationRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
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
) {
    @Transactional
    fun deleteBinnedChats() {
        val cutoff = clock.instant().minus(7, ChronoUnit.DAYS)
        val sessions = sessionRepository.findBinnedBefore(cutoff)
        sessions.forEach { session: BuddySession ->
            citationRepository.deleteAllByMessageSessionId(session.id)
            messageRepository.deleteAllBySessionId(session.id)
            sessionRepository.delete(session)
        }
    }
}
