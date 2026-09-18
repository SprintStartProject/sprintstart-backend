package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.repository.ChatMessageRepository
import com.sprintstart.sprintstartbackend.chat.repository.ChatRepository
import com.sprintstart.sprintstartbackend.chat.repository.CitationRepository
import jakarta.transaction.Transactional
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.temporal.ChronoUnit

@Service
internal class ChatCleanupService(
    private val chatRepository: ChatRepository,
    private val messageRepository: ChatMessageRepository,
    private val citationRepository: CitationRepository,
    private val clock: Clock,
) {
    @Transactional
    fun deleteBinnedChats() {
        val cutoff = clock.instant().minus(7, ChronoUnit.DAYS)
        val chats = chatRepository.findBinnedBefore(cutoff)
        chats.forEach { chat: Chat ->
            citationRepository.deleteAllByMessageChatId(chat.id)
            messageRepository.deleteAllByChatId(chat.id)
            chatRepository.delete(chat)
        }
    }
}
