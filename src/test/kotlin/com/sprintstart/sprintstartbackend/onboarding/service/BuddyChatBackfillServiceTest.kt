package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.chat.models.Chat
import com.sprintstart.sprintstartbackend.chat.models.ChatMessage
import com.sprintstart.sprintstartbackend.chat.models.ChatRole
import com.sprintstart.sprintstartbackend.chat.models.ChatStatus
import com.sprintstart.sprintstartbackend.chat.models.Citation
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
import com.sprintstart.sprintstartbackend.onboarding.model.response.buddy.AiGenerateSessionTitleResponse
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyCitationRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddyMessageRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.BuddySessionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

class BuddyChatBackfillServiceTest {
    private val chatRepository: ChatRepository = mockk()
    private val chatMessageRepository: ChatMessageRepository = mockk()
    private val chatCitationRepository: CitationRepository = mockk()
    private val buddySessionRepository: BuddySessionRepository = mockk()
    private val buddyMessageRepository: BuddyMessageRepository = mockk()
    private val buddyCitationRepository: BuddyCitationRepository = mockk()
    private val buddyAiClient: BuddyAiClient = mockk()

    private lateinit var service: BuddyChatBackfillService

    @BeforeEach
    fun setUp() {
        service = BuddyChatBackfillService(
            chatRepository = chatRepository,
            chatMessageRepository = chatMessageRepository,
            chatCitationRepository = chatCitationRepository,
            buddySessionRepository = buddySessionRepository,
            buddyMessageRepository = buddyMessageRepository,
            buddyCitationRepository = buddyCitationRepository,
            buddyAiClient = buddyAiClient,
        )

        every {
            buddyCitationRepository.saveAll(any<Iterable<BuddyCitation>>())
        } answers {
            firstArg()
        }
    }

    @Test
    fun `does not migrate chat when buddy session already exists`() {
        val chat = mockk<Chat>()
        val chatId = UUID.randomUUID()

        every { chat.id } returns chatId
        every { buddySessionRepository.existsById(chatId) } returns true

        service.migrateChat(chat)

        verify(exactly = 1) {
            buddySessionRepository.existsById(chatId)
        }

        verify(exactly = 0) {
            buddySessionRepository.save(any())
        }

        verify(exactly = 0) {
            chatMessageRepository.findAllByChatIdOrderByCreatedAtAsc(any())
        }
    }

    @Test
    fun `migrates chat without messages`() {
        val chat = mockk<Chat>()

        val chatId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val projectId = UUID.randomUUID()

        every { chat.id } returns chatId
        every { chat.userId } returns userId
        every { chat.title } returns "My chat"
        every { chat.projectId } returns projectId
        every { chat.status } returns ChatStatus.ACTIVE
        every { chat.binnedAt } returns null

        every { buddySessionRepository.existsById(chatId) } returns false
        every {
            buddySessionRepository.save(any<BuddySession>())
        } answers { firstArg() }

        every {
            chatMessageRepository.findAllByChatIdOrderByCreatedAtAsc(chatId)
        } returns emptyList()

        service.migrateChat(chat)

        verify {
            buddySessionRepository.save(
                match {
                    it.id == chatId &&
                        it.userId == userId &&
                        it.title == "My chat" &&
                        it.projectId == projectId &&
                        it.status == BuddySessionStatus.ACTIVE &&
                        it.binnedAt == null
                },
            )
        }

        verify {
            chatMessageRepository.findAllByChatIdOrderByCreatedAtAsc(chatId)
        }

        verify(exactly = 0) {
            buddyMessageRepository.save(any())
        }
    }

    @Test
    fun `migrates messages and their citations`() {
        val chat = mockk<Chat>()
        val chatMessage = mockk<ChatMessage>()
        val citation = mockk<Citation>()

        val chatId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val messageId = UUID.randomUUID()
        val citationId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val createdAt = OffsetDateTime.parse("2026-10-01T10:00:00Z")

        every { chat.id } returns chatId
        every { chat.userId } returns userId
        every { chat.title } returns "My chat"
        every { chat.projectId } returns null
        every { chat.status } returns ChatStatus.ACTIVE
        every { chat.binnedAt } returns null

        every { buddySessionRepository.existsById(chatId) } returns false
        every {
            buddySessionRepository.save(any<BuddySession>())
        } answers { firstArg() }

        every {
            chatMessageRepository.findAllByChatIdOrderByCreatedAtAsc(chatId)
        } returns listOf(chatMessage)

        every { chatMessage.id } returns messageId
        every { chatMessage.role } returns ChatRole.USER
        every { chatMessage.content } returns "Where is the documentation?"
        every { chatMessage.createdAt } returns createdAt
        every { chatMessage.isIncomplete } returns false

        val savedSession = slot<BuddySession>()

        every {
            buddySessionRepository.save(capture(savedSession))
        } answers { firstArg() }

        every {
            buddyMessageRepository.save(any<BuddyMessage>())
        } answers { firstArg() }

        every {
            chatCitationRepository.findAllByMessageId(messageId)
        } returns listOf(citation)

        every { citation.id } returns citationId
        every { citation.artifactId } returns artifactId
        every { citation.filename } returns "architecture.md"
        every { citation.sourceUrl } returns "https://example.com/architecture.md"
        every { citation.startLine } returns 42
        every { citation.startPage } returns 3

        service.migrateChat(chat)

        verify {
            buddyMessageRepository.save(
                match {
                    it.id == messageId &&
                        it.session.id == chatId &&
                        it.role == BuddyMessageRole.USER &&
                        it.content == "Where is the documentation?" &&
                        it.createdAt == createdAt.toInstant() &&
                        !it.isIncomplete
                },
            )
        }

        verify {
            buddyCitationRepository.saveAll(
                match<Iterable<BuddyCitation>> {
                    val citations = it.toList()

                    citations.size == 1 &&
                        citations.first().id == citationId &&
                        citations.first().artifactId == artifactId &&
                        citations.first().filename == "architecture.md" &&
                        citations.first().sourceUrl == "https://example.com/architecture.md" &&
                        citations.first().startLine == 42 &&
                        citations.first().startPage == 3 &&
                        citations.first().message.id == messageId
                },
            )
        }
    }

    @Test
    suspend fun `generates title and resets existing buddy session`() {
        val session = mockk<BuddySession>(relaxed = true)
        val message = mockk<BuddyMessage>()

        val sessionId = UUID.randomUUID()
        val projectId = UUID.randomUUID()

        every { session.id } returns sessionId
        every { session.title } returns ""
        every { session.projectId = any() } just runs
        every { session.status = any() } just runs
        every { session.binnedAt = any() } just runs

        every {
            buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)
        } returns listOf(message)

        every { message.role } returns BuddyMessageRole.USER
        every { message.content } returns "Where is the architecture?"

        coEvery {
            buddyAiClient.getSessionTitle(
                AiGenerateSessionTitleRequest("Where is the architecture?"),
            )
        } returns AiGenerateSessionTitleResponse("Architecture documentation")

        every {
            buddySessionRepository.save(session)
        } returns session

        every {
            buddySessionRepository.findAll()
        } returns listOf(session)

        coEvery {
            chatRepository.findAll()
        } returns emptyList()

        service.run()

        coVerify {
            buddyAiClient.getSessionTitle(
                AiGenerateSessionTitleRequest("Where is the architecture?"),
            )
        }

        verify(atLeast = 1) {
            buddySessionRepository.save(session)
        }

        verify {
            session.projectId = null
            session.status = BuddySessionStatus.ACTIVE
            session.binnedAt = null
        }
    }

    @Test
    suspend fun `does not modify buddy session when it already has a title`() {
        val session = mockk<BuddySession>(relaxed = true)

        val sessionId = UUID.randomUUID()

        every { session.id } returns sessionId
        every { session.title } returns "Existing title"

        every {
            buddySessionRepository.findAll()
        } returns listOf(session)

        every {
            chatRepository.findAll()
        } returns emptyList()

        service.run()

        verify(exactly = 0) {
            buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(any())
        }

        coVerify(exactly = 0) {
            buddyAiClient.getSessionTitle(any())
        }

        verify(exactly = 0) {
            buddySessionRepository.save(any())
        }
    }

    @Test
    suspend fun `resets untitled session without generating title when there is no user message`() {
        val session = mockk<BuddySession>(relaxed = true)

        val sessionId = UUID.randomUUID()

        every { session.id } returns sessionId
        every { session.title } returns ""

        every {
            buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)
        } returns emptyList()

        every {
            buddySessionRepository.findAll()
        } returns listOf(session)

        every {
            buddySessionRepository.save(session)
        } returns session

        every {
            chatRepository.findAll()
        } returns emptyList()

        service.run()

        coVerify(exactly = 0) {
            buddyAiClient.getSessionTitle(any())
        }

        verify {
            buddySessionRepository.save(session)
        }

        verify {
            session.projectId = null
            session.status = BuddySessionStatus.ACTIVE
            session.binnedAt = null
        }
    }

    @Test
    suspend fun `continues backfill when title generation fails`() {
        val session = mockk<BuddySession>(relaxed = true)
        val message = mockk<BuddyMessage>()

        val sessionId = UUID.randomUUID()

        every { session.id } returns sessionId
        every { session.title } returns ""

        every {
            buddyMessageRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId)
        } returns listOf(message)

        every { message.role } returns BuddyMessageRole.USER
        every { message.content } returns "Where is the architecture?"

        coEvery {
            buddyAiClient.getSessionTitle(any())
        } throws AiResponseException("AI unavailable")

        every {
            buddySessionRepository.findAll()
        } returns listOf(session)

        every {
            chatRepository.findAll()
        } returns emptyList()

        every {
            buddySessionRepository.save(session)
        } returns session

        service.run()

        coVerify {
            buddyAiClient.getSessionTitle(
                AiGenerateSessionTitleRequest("Where is the architecture?"),
            )
        }

        verify {
            buddySessionRepository.save(session)
        }

        verify {
            session.projectId = null
            session.status = BuddySessionStatus.ACTIVE
            session.binnedAt = null
        }
    }
}
