package com.sprintstart.sprintstartbackend.buddy.scheduler

import com.sprintstart.sprintstartbackend.buddy.service.BuddySessionCleanupService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BuddySessionCleanupScheduler(
    private val sessionCleanupService: BuddySessionCleanupService,
) {
    @Scheduled(cron = "\${sprintstart.buddy.cleanup.cron}")
    fun deleteSessions() {
        sessionCleanupService.deleteBinnedChats()
    }
}
