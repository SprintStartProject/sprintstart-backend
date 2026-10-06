package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.ChatStatus
import com.sprintstart.sprintstartbackend.chat.repository.ChatMessageRepository
import com.sprintstart.sprintstartbackend.chat.repository.ChatRepository
import com.sprintstart.sprintstartbackend.chat.repository.CitationRepository
import com.sprintstart.sprintstartbackend.onboarding.client.BuddyAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.BuddyMessageRole
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyCitation
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddyMessage
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySession
import com.sprintstart.sprintstartbackend.onboarding.model.entity.BuddySessionStatus
import com.sprintstart.sprintstartbackend.onboarding.model.exceptions.AiResponseException
import com.sprintstart.sprintstartbackend.onboarding.model.request.buddy.AiGenerateSessionTitleRequest
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyCitationRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
internal class BuddyChatBackfillService(
    private val chatRepository: ChatRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatCitationRepository: CitationRepository,
    private val buddySessionRepository: BuddySessionRepository,
    private val buddyMessageRepository: BuddyMessageRepository,
    private val buddyCitationRepository: BuddyCitationRepository,
    private val buddyAiClient: BuddyAiClient,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    suspend fun run() {
        migrateExistingBuddySessions()
        migrateChats()
    }

    private suspend fun migrateChats() {
        chatRepository.findAll().forEach { chat ->
            migrateChat(chat)
        }
    }

    @Transactional
    fun migrateChat(chat: Chat) {
        if (buddySessionRepository.existsById(chat.id)) {
            return
        }

        val session = buddySessionRepository.save(
            BuddySession(
                id = chat.id,
                userId = chat.userId,
                title = chat.title,
                projectId = chat.projectId,
                status = chat.status.toBuddyStatus(),
                binnedAt = chat.binnedAt,
            ),
        )

        val chatMessages = chatMessageRepository
            .findAllByChatIdOrderByCreatedAtAsc(chat.id)

        chatMessages.forEach { chatMessage ->
            val buddyMessage = buddyMessageRepository.save(
                BuddyMessage(
                    id = chatMessage.id,
                    session = session,
                    role = chatMessage.role.toBuddyRole(),
                    content = chatMessage.content,
                    createdAt = chatMessage.createdAt.toInstant(),
                    isIncomplete = chatMessage.isIncomplete,
                ),
            )

            val citations = chatCitationRepository
                .findAllByMessageId(chatMessage.id)

            buddyCitationRepository.saveAll(
                citations.map { citation ->
                    BuddyCitation(
                        id = citation.id,
                        artifactId = citation.artifactId,
                        filename = citation.filename,
                        sourceUrl = citation.sourceUrl,
                        startLine = citation.startLine,
                        startPage = citation.startPage,
                        message = buddyMessage,
                    )
                },
            )
        }
    }

    private suspend fun migrateExistingBuddySessions() {
        buddySessionRepository.findAll().forEach { session ->
            migrateExistingBuddySession(session)
        }
    }

    private suspend fun migrateExistingBuddySession(session: BuddySession) {
        if (session.title.isNotBlank()) {
            return
        }

        val firstUserMessage = buddyMessageRepository
            .findAllBySessionIdOrderByCreatedAtAsc(session.id)
            .firstOrNull { it.role == BuddyMessageRole.USER }

        session.projectId = null
        session.status = BuddySessionStatus.ACTIVE
        session.binnedAt = null

        if (firstUserMessage != null) {
            try {
                val generatedTitle = buddyAiClient
                    .getSessionTitle(AiGenerateSessionTitleRequest(firstUserMessage.content))
                session.title = generatedTitle.title
                buddySessionRepository.save(session)
            } catch (e: AiResponseException) {
                logger.warn("Could not generate buddy session title", e)
            }
        }

        buddySessionRepository.save(session)
    }
}

private fun ChatStatus.toBuddyStatus(): BuddySessionStatus =
    when (this) {
        ChatStatus.ACTIVE -> BuddySessionStatus.ACTIVE
        ChatStatus.BINNED -> BuddySessionStatus.BINNED
    }

private fun ChatRole.toBuddyRole(): BuddyMessageRole =
    when (this) {
        ChatRole.USER -> BuddyMessageRole.USER
        ChatRole.ASSISTANT -> BuddyMessageRole.ASSISTANT
        ChatRole.SYSTEM -> BuddyMessageRole.SYSTEM
    }
