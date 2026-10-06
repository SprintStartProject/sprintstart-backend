package com.sprintstart.sprintstartbackend.onboarding.scheduler

import com.sprintstart.sprintstartbackend.onboarding.service.BuddySessionCleanupService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BuddySessionCleanupScheduler(
    private val sessionCleanupService: BuddySessionCleanupService,
) {
    @Scheduled(cron = "\${sprintstart.chat.cleanup.cron}")
    fun deleteSessions() {
        sessionCleanupService.deleteBinnedChats()
    }
}
