package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.FaqInsightsConfig
import com.sprintstart.sprintstartbackend.chat.external.events.ChatQuestionAskedEvent
import com.sprintstart.sprintstartbackend.insights.InsightsAiClient
import com.sprintstart.sprintstartbackend.insights.insightsTestConfig
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqClassifyRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqClassifyResponse
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqDocument
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import com.sprintstart.sprintstartbackend.insights.repository.FaqGroupRepository
import com.sprintstart.sprintstartbackend.insights.repository.FaqQuestionRepository
import io.mockk.CapturingSlot
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.util.Optional
import java.util.UUID

class FaqLiveUpdateServiceTest {
    private val projectId: UUID = UUID.randomUUID()
    private val askedAt: Instant = Instant.parse("2026-08-14T09:00:00Z")

    private val faqGroupRepository = mockk<FaqGroupRepository>()
    private val faqQuestionRepository = mockk<FaqQuestionRepository>()
    private val insightsAiClient = mockk<InsightsAiClient>()

    // Relaxed: the limits are enforced by their own service, covered in its own test. What
    // matters here is only that filing a question hands off to it.
    private val faqConsolidationService = mockk<FaqConsolidationService>(relaxed = true)

    // Relaxed: TransactionTemplate only needs a manager to hand it a status; the callback
    // runs inline either way.
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private fun serviceWith(faq: FaqInsightsConfig = FaqInsightsConfig()) = FaqLiveUpdateService(
        faqGroupRepository = faqGroupRepository,
        faqQuestionRepository = faqQuestionRepository,
        insightsAiClient = insightsAiClient,
        faqConsolidationService = faqConsolidationService,
        applicationConfig = insightsTestConfig(faq = faq),
        transactionManager = transactionManager,
    )

    private fun event(question: String = "How do I get VPN access?") = ChatQuestionAskedEvent(
        messageId = UUID.randomUUID(),
        chatId = UUID.randomUUID(),
        projectId = projectId,
        question = question,
        askedAt = askedAt,
    )

    private fun existingGroup(
        question: String = "How do I get VPN access?",
        category: String? = "Access & Accounts",
        count: Int = 3,
        lastAskedAt: Instant = askedAt.minusSeconds(3600),
    ) = FaqGroup(
        projectId = projectId,
        question = question,
        occurrenceCount = count,
        category = category,
        firstAskedAt = lastAskedAt,
        lastAskedAt = lastAskedAt,
    )

    private fun givenGroups(vararg groups: FaqGroup) {
        every { faqQuestionRepository.existsBySourceMessageId(any()) } returns false
        every { faqGroupRepository.findAllByProjectIdOrderByOccurrenceCountDesc(projectId) } returns groups.toList()
        every { faqGroupRepository.save(any<FaqGroup>()) } answers { firstArg() }
    }

    private fun captureSaved(): CapturingSlot<FaqGroup> {
        val saved = slot<FaqGroup>()
        every { faqGroupRepository.save(capture(saved)) } answers { saved.captured }
        return saved
    }

    @Test
    fun `opens a new group when the question does not match an existing one`() = runTest {
        givenGroups()
        val saved = captureSaved()
        coEvery { insightsAiClient.classifyFaqQuestion(any()) } returns AiFaqClassifyResponse(
            relevant = true,
            question = "How do I get VPN access?",
            category = "Access & Accounts",
            groupId = null,
            documents = listOf(AiFaqDocument(id = "doc_001", title = "VPN Setup Guide", source = "confluence")),
        )

        serviceWith().onQuestionAsked(event())

        val group = saved.captured
        assertEquals(1, group.occurrenceCount)
        assertEquals("Access & Accounts", group.category)
        assertEquals(askedAt, group.firstAskedAt)
        assertEquals(askedAt, group.lastAskedAt)
        assertEquals("doc_001", group.documents.single().documentRef)
        assertEquals(askedAt, group.questions.single().askedAt)
    }

    @Test
    fun `joins an existing group and moves its recency forward`() = runTest {
        val group = existingGroup()
        givenGroups(group)
        every { faqGroupRepository.findByIdAndProjectId(group.id, projectId) } returns Optional.of(group)
        coEvery { insightsAiClient.classifyFaqQuestion(any()) } returns AiFaqClassifyResponse(
            relevant = true,
            question = "Can someone enable VPN for me?",
            category = "Access & Accounts",
            groupId = group.id.toString(),
        )

        serviceWith().onQuestionAsked(event("Can someone enable VPN for me?"))

        assertEquals(4, group.occurrenceCount)
        assertEquals(askedAt, group.lastAskedAt)
        assertEquals("Can someone enable VPN for me?", group.questions.single().text)
    }

    @Test
    fun `keeps the redacted text rather than the raw question`() = runTest {
        givenGroups()
        val saved = captureSaved()
        coEvery { insightsAiClient.classifyFaqQuestion(any()) } returns AiFaqClassifyResponse(
            relevant = true,
            question = "Ask [NAME] for VPN access",
            category = "Access & Accounts",
        )

        serviceWith().onQuestionAsked(event("Ask John Doe for VPN access"))

        assertEquals("Ask [NAME] for VPN access", saved.captured.question)
        assertEquals(
            "Ask [NAME] for VPN access",
            saved.captured.questions
                .single()
                .text,
        )
    }

    @Test
    fun `drops a question the AI service classifies as smalltalk`() = runTest {
        givenGroups()
        coEvery { insightsAiClient.classifyFaqQuestion(any()) } returns AiFaqClassifyResponse(relevant = false)

        serviceWith().onQuestionAsked(event("hey there, how you doing"))

        coVerify(exactly = 0) { faqGroupRepository.save(any<FaqGroup>()) }
    }

    @Test
    fun `skips a message that was already filed`() = runTest {
        every { faqQuestionRepository.existsBySourceMessageId(any()) } returns true

        serviceWith().onQuestionAsked(event())

        // Not even the classification runs: a redelivered event must cost nothing, and counting
        // the same message twice would quietly inflate whichever group it lands in.
        coVerify(exactly = 0) { insightsAiClient.classifyFaqQuestion(any()) }
    }

    @Test
    fun `does nothing when live updates are switched off`() = runTest {
        serviceWith(FaqInsightsConfig(liveUpdates = false)).onQuestionAsked(event())

        coVerify(exactly = 0) { insightsAiClient.classifyFaqQuestion(any()) }
        coVerify(exactly = 0) { faqQuestionRepository.existsBySourceMessageId(any()) }
    }

    @Test
    fun `opens a new group when the AI service returns an unknown group id`() = runTest {
        givenGroups(existingGroup())
        val saved = captureSaved()
        every { faqGroupRepository.findByIdAndProjectId(any(), projectId) } returns Optional.empty()
        coEvery { insightsAiClient.classifyFaqQuestion(any()) } returns AiFaqClassifyResponse(
            relevant = true,
            question = "How do I start the backend?",
            category = "Local Setup",
            groupId = UUID.randomUUID().toString(),
        )

        serviceWith().onQuestionAsked(event("How do I start the backend?"))

        assertEquals(1, saved.captured.occurrenceCount)
        assertEquals("Local Setup", saved.captured.category)
    }

    @Test
    fun `opens a new group when the AI service returns a group id that is not a uuid`() = runTest {
        givenGroups(existingGroup())
        val saved = captureSaved()
        coEvery { insightsAiClient.classifyFaqQuestion(any()) } returns AiFaqClassifyResponse(
            relevant = true,
            question = "How do I start the backend?",
            category = "Local Setup",
            groupId = "not-a-uuid",
        )

        serviceWith().onQuestionAsked(event("How do I start the backend?"))

        assertEquals(1, saved.captured.occurrenceCount)
    }

    @Test
    fun `snaps a differently cased category onto the spelling already in use`() = runTest {
        givenGroups(existingGroup(category = "Local Setup"))
        val saved = captureSaved()
        coEvery { insightsAiClient.classifyFaqQuestion(any()) } returns AiFaqClassifyResponse(
            relevant = true,
            question = "How do I start the backend?",
            category = "local  setup",
        )

        serviceWith().onQuestionAsked(event("How do I start the backend?"))

        // Two spellings would read to a PM as the same topic listed twice.
        assertEquals("Local Setup", saved.captured.category)
    }

    @Test
    fun `leaves a group uncategorized when the AI service names no category`() = runTest {
        givenGroups()
        val saved = captureSaved()
        coEvery { insightsAiClient.classifyFaqQuestion(any()) } returns AiFaqClassifyResponse(
            relevant = true,
            question = "How do I get VPN access?",
            category = "   ",
        )

        serviceWith().onQuestionAsked(event())

        assertNull(saved.captured.category)
    }

    @Test
    fun `sends the existing structure so the AI service can match against it`() = runTest {
        givenGroups(existingGroup(count = 5), existingGroup(question = "How do I start the backend?", count = 2))
        captureSaved()
        val request = slot<AiFaqClassifyRequest>()
        coEvery { insightsAiClient.classifyFaqQuestion(capture(request)) } returns
            AiFaqClassifyResponse(relevant = true, question = "How do I deploy?", category = "Deployment")

        serviceWith().onQuestionAsked(event("How do I deploy?"))

        val sent = request.captured
        assertEquals(projectId.toString(), sent.projectId)
        assertEquals("How do I deploy?", sent.question)
        assertEquals(listOf("Access & Accounts"), sent.categories.map { it.name })
        assertEquals(7, sent.categories.single().questionCount)
        assertEquals(2, sent.groups.size)
    }

    @Test
    fun `enforces the structure limits after filing a question`() = runTest {
        givenGroups()
        captureSaved()
        coEvery { insightsAiClient.classifyFaqQuestion(any()) } returns AiFaqClassifyResponse(
            relevant = true,
            question = "How do I get VPN access?",
            category = "Access & Accounts",
        )

        serviceWith().onQuestionAsked(event())

        coVerify(exactly = 1) { faqConsolidationService.enforceLimits(projectId, "Access & Accounts") }
    }

    @Test
    fun `caps how many candidate groups one classification may consider`() = runTest {
        val groups = (1..30).map { existingGroup(question = "Question $it", count = it) }
        givenGroups(*groups.toTypedArray())
        captureSaved()
        val request = slot<AiFaqClassifyRequest>()
        coEvery { insightsAiClient.classifyFaqQuestion(capture(request)) } returns
            AiFaqClassifyResponse(relevant = true, question = "How do I deploy?", category = "Deployment")

        serviceWith(FaqInsightsConfig(candidateGroups = 10)).onQuestionAsked(event("How do I deploy?"))

        // The bound is the whole point: without it the prompt would grow with everything the
        // project ever asked, which is the cost the incremental path exists to avoid.
        assertEquals(10, request.captured.groups.size)
        assertTrue(request.captured.groups.any { it.question == "Question 30" })
    }
}
