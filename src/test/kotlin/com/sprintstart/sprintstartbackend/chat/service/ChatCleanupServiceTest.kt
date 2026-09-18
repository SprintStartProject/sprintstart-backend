package com.sprintstart.sprintstartbackend.chat.service

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.repository.ChatMessageRepository
import com.sprintstart.sprintstartbackend.chat.repository.ChatRepository
import com.sprintstart.sprintstartbackend.chat.repository.CitationRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.test.Test

class ChatCleanupServiceTest {
    private val chatRepository: ChatRepository = mockk()
    private val messageRepository: ChatMessageRepository = mockk()
    private val citationRepository: CitationRepository = mockk()
    private val clock = mockk<Clock>()
    private val chatCleanupService = ChatCleanupService(
        chatRepository,
        messageRepository,
        citationRepository,
        clock,
    )

    @Test
    fun `deletes binned chat including citations and messages`() {
        val now = Instant.parse("2026-09-17T10:00:00Z")
        val cutoff = now.minus(7, ChronoUnit.DAYS)

        val chatId = UUID.randomUUID()
        val chat = mockk<Chat>()

        every { chat.id } returns chatId
        every { clock.instant() } returns now
        every { chatRepository.findBinnedBefore(cutoff) } returns listOf(chat)

        every { citationRepository.deleteAllByMessageChatId(chatId) } returns Unit
        every { messageRepository.deleteAllByChatId(chatId) } returns Unit
        every { chatRepository.delete(chat) } returns Unit

        chatCleanupService.deleteBinnedChats()

        verify {
            chatRepository.findBinnedBefore(cutoff)
            citationRepository.deleteAllByMessageChatId(chatId)
            messageRepository.deleteAllByChatId(chatId)
            chatRepository.delete(chat)
        }
    }

    @Test
    fun `does nothing when no binned chats are expired`() {
        val now = Instant.parse("2026-09-17T10:00:00Z")
        val cutoff = now.minus(7, ChronoUnit.DAYS)

        every { clock.instant() } returns now
        every { chatRepository.findBinnedBefore(cutoff) } returns emptyList()

        chatCleanupService.deleteBinnedChats()

        verify(exactly = 1) {
            chatRepository.findBinnedBefore(cutoff)
        }

        verify(exactly = 0) {
            citationRepository.deleteAllByMessageChatId(any())
        }

        verify(exactly = 0) {
            messageRepository.deleteAllByChatId(any())
        }

        verify(exactly = 0) {
            chatRepository.delete(any())
        }
    }
}
