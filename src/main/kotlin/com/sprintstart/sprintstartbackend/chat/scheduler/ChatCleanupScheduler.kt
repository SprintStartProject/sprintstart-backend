package com.sprintstart.sprintstartbackend.chat.scheduler

import com.sprintstart.sprintstartbackend.chat.service.ChatCleanupService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
internal class ChatCleanupScheduler(
    private val chatCleanupService: ChatCleanupService,
) {
    @Scheduled(cron = "\${sprintstart.chat.cleanup.cron}")
    fun deleteChats() {
        chatCleanupService.deleteBinnedChats()
    }
}
