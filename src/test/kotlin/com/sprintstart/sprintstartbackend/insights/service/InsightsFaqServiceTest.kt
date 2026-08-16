package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.FaqInsightsConfig
import com.sprintstart.sprintstartbackend.chat.external.ChatQuestion
import com.sprintstart.sprintstartbackend.chat.external.ChatQuestionApi
import com.sprintstart.sprintstartbackend.insights.InsightsAiClient
import com.sprintstart.sprintstartbackend.insights.insightsTestConfig
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqDocument
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroup
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupingRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupingResponse
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqSampleQuestion
import com.sprintstart.sprintstartbackend.insights.model.dto.request.FaqRebuildScope
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqDocument
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqQuestion
import com.sprintstart.sprintstartbackend.insights.model.exceptions.InsightsAiException
import com.sprintstart.sprintstartbackend.insights.model.mapper.AiFaqGroupMapper
import com.sprintstart.sprintstartbackend.insights.model.mapper.FaqResponseMapper
import com.sprintstart.sprintstartbackend.insights.repository.FaqGroupRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import java.util.UUID

class InsightsFaqServiceTest {
    private val faqGroupRepository = mockk<FaqGroupRepository>()
    private val projectId: UUID = UUID.randomUUID()
    private val insightsAiClient = mockk<InsightsAiClient>()
    private val chatQuestionApi = mockk<ChatQuestionApi>()
    private val aiFaqGroupMapper = AiFaqGroupMapper()
    private val applicationConfig = insightsTestConfig()
    private val faqResponseMapper = FaqResponseMapper(applicationConfig)

    // Relaxed: no test here asserts on trends, and the calculator's own behaviour is covered
    // separately; an empty stat map is the same "no data yet" case a fresh project produces.
    private val faqTrendCalculator = mockk<FaqTrendCalculator>(relaxed = true)

    // Relaxed: TransactionTemplate only needs a manager to hand it a status; the callback
    // runs inline either way.
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private val service = InsightsFaqService(
        faqGroupRepository = faqGroupRepository,
        insightsAiClient = insightsAiClient,
        chatQuestionApi = chatQuestionApi,
        aiFaqGroupMapper = aiFaqGroupMapper,
        faqResponseMapper = faqResponseMapper,
        faqTrendCalculator = faqTrendCalculator,
        applicationConfig = applicationConfig,
        transactionManager = transactionManager,
    )

    private fun buildGroup(): FaqGroup {
        val group = FaqGroup(question = "How do I get VPN access?", occurrenceCount = 14)
        group.questions.add(FaqQuestion(text = "How do I get VPN access?", group = group))
        group.documents.add(
            FaqDocument(
                documentRef = "doc_001",
                title = "VPN Setup Guide",
                source = "confluence",
                group = group,
            ),
        )
        return group
    }

    @Test
    fun `getFaqOverview maps groups and exposes the document reference as the id`() {
        val group = buildGroup()
        every { faqGroupRepository.findAllByProjectIdOrderByOccurrenceCountDesc(projectId) } returns listOf(group)
        every { chatQuestionApi.countUserQuestionsForProject(projectId, null) } returns 14

        val overview = service.getFaqOverview(projectId)

        assertEquals(1, overview.groups.size)
        val summary = overview.groups.first()
        assertEquals(group.id, summary.groupId)
        assertEquals(14, summary.count)
        assertEquals("How do I get VPN access?", summary.question)
        assertEquals("doc_001", summary.topDocuments.first().id)
        assertEquals("VPN Setup Guide", summary.topDocuments.first().title)
    }

    @Test
    fun `getFaqGroup maps questions and answering documents`() {
        val group = buildGroup()
        every { faqGroupRepository.findByIdAndProjectId(group.id, projectId) } returns Optional.of(group)

        val detail = service.getFaqGroup(projectId, group.id)

        assertEquals(group.id, detail.groupId)
        assertEquals(14, detail.count)
        assertEquals("How do I get VPN access?", detail.questions.first().text)
        assertEquals("doc_001", detail.answeringDocuments.first().id)
        assertEquals("confluence", detail.answeringDocuments.first().source)
    }

    @Test
    fun `getFaqGroup throws 404 when the group does not exist`() {
        val missingId = UUID.randomUUID()
        every { faqGroupRepository.findByIdAndProjectId(missingId, projectId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.getFaqGroup(projectId, missingId)
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `refreshFaqGroups groups via the AI service and rebuilds the cache`() = runTest {
        val askedAt = Instant.parse("2026-08-01T10:00:00Z")
        val messageId = UUID.randomUUID()
        val aiResponse = AiFaqGroupingResponse(
            groups = listOf(
                AiFaqGroup(
                    question = "How do I get VPN access?",
                    count = 14,
                    questions = listOf(
                        AiFaqSampleQuestion(ids = listOf(messageId.toString()), text = "How do I get VPN access?"),
                        AiFaqSampleQuestion(
                            ids = listOf(UUID.randomUUID().toString()),
                            text = "Can someone enable VPN for me?",
                        ),
                    ),
                    documents = listOf(
                        AiFaqDocument(
                            id = "doc_001",
                            title = "VPN Setup Guide",
                            source = "confluence",
                        ),
                    ),
                    title = "Getting VPN access",
                    questionIds = listOf(messageId.toString()),
                ),
            ),
        )
        every { chatQuestionApi.getUserQuestionsForProject(projectId) } returns
            listOf(ChatQuestion(id = messageId, text = "How do I get VPN access?", askedAt = askedAt))
        every { chatQuestionApi.countUserQuestionsForProject(projectId, null) } returns 1
        val requestSlot = slot<AiFaqGroupingRequest>()
        coEvery { insightsAiClient.groupFaqQuestions(capture(requestSlot)) } returns aiResponse
        every { faqGroupRepository.deleteAllByProjectId(projectId) } just Runs
        every { faqGroupRepository.deleteAllByProjectIdIsNull() } just Runs
        val savedSlot = slot<List<FaqGroup>>()
        every { faqGroupRepository.saveAll(capture(savedSlot)) } answers { savedSlot.captured.toMutableList() }

        val result = service.refreshFaqGroups(projectId, FaqRebuildScope.EVERYTHING)

        assertEquals(1, result.groupCount)

        val sentRequest = requestSlot.captured
        assertEquals(1, sentRequest.questions.size)
        assertEquals("How do I get VPN access?", sentRequest.questions.first().text)

        val persisted = savedSlot.captured.first()
        assertEquals("How do I get VPN access?", persisted.question)
        assertEquals(14, persisted.occurrenceCount)
        // One row per ask, not per sampled phrasing: the trend counts rows, and a rebuilt group
        // whose repeats were sampled away would read as quieter than it is.
        assertEquals(2, persisted.questions.count { it.text.isNotBlank() })
        assertEquals("doc_001", persisted.documents.first().documentRef)
        assertEquals("Getting VPN access", persisted.title)
        // Recovered from the chat message, not stamped with "now": a rebuilt group that looks
        // freshly asked would tell a PM the opposite of the truth about a dormant topic.
        assertEquals(askedAt, persisted.lastAskedAt)
        assertEquals(askedAt, persisted.questions.first().askedAt)
        assertEquals(messageId, persisted.questions.first().sourceMessageId)

        coVerify(exactly = 1) { insightsAiClient.groupFaqQuestions(any()) }
        verifyOrder {
            faqGroupRepository.deleteAllByProjectId(projectId)
            faqGroupRepository.saveAll(any<List<FaqGroup>>())
        }
    }

    @Test
    fun `refreshFaqGroups clears the cache even when the AI returns no groups`() = runTest {
        every { chatQuestionApi.getUserQuestionsForProject(projectId) } returns emptyList()
        every { chatQuestionApi.countUserQuestionsForProject(projectId, null) } returns 0
        coEvery { insightsAiClient.groupFaqQuestions(any()) } returns AiFaqGroupingResponse(groups = emptyList())
        every { faqGroupRepository.deleteAllByProjectId(projectId) } just Runs
        every { faqGroupRepository.deleteAllByProjectIdIsNull() } just Runs
        every { faqGroupRepository.saveAll(any<List<FaqGroup>>()) } answers { mutableListOf() }

        val result = service.refreshFaqGroups(projectId, FaqRebuildScope.EVERYTHING)

        assertEquals(0, result.groupCount)
        verify(exactly = 1) { faqGroupRepository.deleteAllByProjectId(projectId) }
    }

    private fun questions(count: Int): List<ChatQuestion> =
        (1..count).map {
            ChatQuestion(
                id = UUID.randomUUID(),
                text = "Question $it",
                askedAt = Instant.parse("2026-08-01T10:00:00Z").plusSeconds(it.toLong()),
            )
        }

    private fun givenQuestions(chatQuestions: List<ChatQuestion>) {
        every { chatQuestionApi.getUserQuestionsForProject(projectId) } returns chatQuestions
        every { chatQuestionApi.countUserQuestionsForProject(projectId, null) } returns chatQuestions.size.toLong()
        every { faqGroupRepository.deleteAllByProjectId(projectId) } just Runs
        every { faqGroupRepository.deleteAllByProjectIdIsNull() } just Runs
        every { faqGroupRepository.saveAll(any<List<FaqGroup>>()) } answers
            { firstArg<List<FaqGroup>>().toMutableList() }
    }

    private fun singletonGroups(chatQuestions: List<ChatQuestion>) = AiFaqGroupingResponse(
        groups = chatQuestions.map {
            AiFaqGroup(question = it.text, count = 1, title = it.text, questionIds = listOf(it.id.toString()))
        },
    )

    @Test
    fun `refreshFaqGroups sends at most the configured number of questions`() = runTest {
        val service = InsightsFaqService(
            faqGroupRepository = faqGroupRepository,
            insightsAiClient = insightsAiClient,
            chatQuestionApi = chatQuestionApi,
            aiFaqGroupMapper = aiFaqGroupMapper,
            faqResponseMapper = faqResponseMapper,
            faqTrendCalculator = faqTrendCalculator,
            applicationConfig = insightsTestConfig(faq = FaqInsightsConfig(rebuildQuestionLimit = 3)),
            transactionManager = transactionManager,
        )
        val chatQuestions = questions(10)
        givenQuestions(chatQuestions)
        val requestSlot = slot<AiFaqGroupingRequest>()
        coEvery { insightsAiClient.groupFaqQuestions(capture(requestSlot)) } returns AiFaqGroupingResponse(emptyList())

        service.refreshFaqGroups(projectId, FaqRebuildScope.EVERYTHING)

        // The newest ones, but back in chronological order: the AI service treats a cluster's
        // first member as its representative, and the oldest phrasing is the established one.
        assertEquals(3, requestSlot.captured.questions.size)
        assertEquals(
            listOf("Question 8", "Question 9", "Question 10"),
            requestSlot.captured.questions.map { it.text },
        )
    }

    @Test
    fun `refreshFaqGroups keeps the previous FAQ when the AI grouped nothing`() = runTest {
        val chatQuestions = questions(25)
        givenQuestions(chatQuestions)
        coEvery { insightsAiClient.groupFaqQuestions(any()) } returns singletonGroups(chatQuestions)

        // One entry per question is the AI service's own "could not parse" fallback. Applying it
        // would delete a working FAQ and silently replace it with 25 single-count entries.
        assertThrows<InsightsAiException> { service.refreshFaqGroups(projectId, FaqRebuildScope.EVERYTHING) }
        verify(exactly = 0) { faqGroupRepository.deleteAllByProjectId(projectId) }
    }

    @Test
    fun `refreshFaqGroups accepts a result that grouped at least something`() = runTest {
        val chatQuestions = questions(25)
        givenQuestions(chatQuestions)
        val grouped = singletonGroups(chatQuestions).let { response ->
            AiFaqGroupingResponse(
                groups = response.groups.mapIndexed { index, group ->
                    if (index == 0) group.copy(count = 2) else group
                },
            )
        }
        coEvery { insightsAiClient.groupFaqQuestions(any()) } returns grouped

        // A single real cluster is proof the model did its job; the rest genuinely being distinct
        // is a legitimate answer.
        assertEquals(25, service.refreshFaqGroups(projectId, FaqRebuildScope.EVERYTHING).groupCount)
    }

    @Test
    fun `refreshFaqGroups allows one entry per question for a small project`() = runTest {
        val chatQuestions = questions(5)
        givenQuestions(chatQuestions)
        coEvery { insightsAiClient.groupFaqQuestions(any()) } returns singletonGroups(chatQuestions)

        // Five questions with nothing in common is entirely plausible; the guard must not turn a
        // young project's honest result into an error.
        assertEquals(5, service.refreshFaqGroups(projectId, FaqRebuildScope.EVERYTHING).groupCount)
    }

    @Test
    fun `getFaqOverview reports the material a rebuild has and the ceiling on it`() {
        every { faqGroupRepository.findAllByProjectIdOrderByOccurrenceCountDesc(projectId) } returns emptyList()
        every { chatQuestionApi.countUserQuestionsForProject(projectId, null) } returns 5_000

        val overview = service.getFaqOverview(projectId)

        // Uncapped, so a client can tell that the ceiling would bite and say so before the click.
        assertEquals(5_000, overview.questionCount)
        assertEquals(applicationConfig.insights.faq.rebuildQuestionLimit, overview.rebuildQuestionLimit)
    }

    @Test
    fun `refreshFaqGroups narrows to the requested scope`() = runTest {
        val old = Instant.now().minus(200, java.time.temporal.ChronoUnit.DAYS)
        val recent = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS)
        val chatQuestions = listOf(
            ChatQuestion(id = UUID.randomUUID(), text = "Old question", askedAt = old),
            ChatQuestion(id = UUID.randomUUID(), text = "Recent question", askedAt = recent),
        )
        givenQuestions(chatQuestions)
        val requestSlot = slot<AiFaqGroupingRequest>()
        coEvery { insightsAiClient.groupFaqQuestions(capture(requestSlot)) } returns AiFaqGroupingResponse(emptyList())

        service.refreshFaqGroups(projectId, FaqRebuildScope(sinceDays = 30))

        // Everything outside the window is dropped — that is the point of asking for it, and why
        // the caller has to choose it rather than getting it applied quietly.
        assertEquals(listOf("Recent question"), requestSlot.captured.questions.map { it.text })
    }

    @Test
    fun `refreshFaqGroups never lets a scope exceed the configured ceiling`() = runTest {
        val service = InsightsFaqService(
            faqGroupRepository = faqGroupRepository,
            insightsAiClient = insightsAiClient,
            chatQuestionApi = chatQuestionApi,
            aiFaqGroupMapper = aiFaqGroupMapper,
            faqResponseMapper = faqResponseMapper,
            faqTrendCalculator = faqTrendCalculator,
            applicationConfig = insightsTestConfig(faq = FaqInsightsConfig(rebuildQuestionLimit = 2)),
            transactionManager = transactionManager,
        )
        givenQuestions(questions(10))
        val requestSlot = slot<AiFaqGroupingRequest>()
        coEvery { insightsAiClient.groupFaqQuestions(capture(requestSlot)) } returns AiFaqGroupingResponse(emptyList())

        service.refreshFaqGroups(projectId, FaqRebuildScope(questionLimit = 9))

        // The scope can only ask for less. A caller must not be able to opt out of the bound that
        // keeps this prompt from growing without limit.
        assertEquals(2, requestSlot.captured.questions.size)
    }

    @Test
    fun `refreshFaqGroups stores a row for every ask, not just the sampled phrasings`() = runTest {
        val repeated = List(3) { UUID.randomUUID() }
        val askedAt = Instant.parse("2026-08-01T10:00:00Z")
        givenQuestions(
            repeated.map { ChatQuestion(id = it, text = "How do I get VPN access?", askedAt = askedAt) },
        )
        coEvery { insightsAiClient.groupFaqQuestions(any()) } returns AiFaqGroupingResponse(
            groups = listOf(
                AiFaqGroup(
                    question = "How do I get VPN access?",
                    count = 3,
                    // One phrasing, but every asker of it: three identical asks are three asks,
                    // and the sample carries all three ids rather than only the first.
                    questions = listOf(
                        AiFaqSampleQuestion(
                            ids = repeated.map { it.toString() },
                            text = "How do I get VPN access?",
                        ),
                    ),
                    title = "Getting VPN access",
                    questionIds = repeated.map { it.toString() },
                ),
            ),
        )
        val savedSlot = slot<List<FaqGroup>>()
        every { faqGroupRepository.saveAll(capture(savedSlot)) } answers { savedSlot.captured.toMutableList() }

        service.refreshFaqGroups(projectId, FaqRebuildScope.EVERYTHING)

        val persisted = savedSlot.captured.single()
        assertEquals(3, persisted.questions.size)
        // Every one keeps the phrasing, so the detail view's "(3x)" is right too — not just the
        // group's total.
        assertEquals(3, persisted.questions.count { it.text.isNotBlank() })
        // Every ask carries its origin, so a redelivered event cannot count one of them twice.
        assertEquals(repeated.toSet(), persisted.questions.mapNotNull { it.sourceMessageId }.toSet())
    }

    @Test
    fun `previewRebuild caps each window the way a rebuild would`() {
        val service = InsightsFaqService(
            faqGroupRepository = faqGroupRepository,
            insightsAiClient = insightsAiClient,
            chatQuestionApi = chatQuestionApi,
            aiFaqGroupMapper = aiFaqGroupMapper,
            faqResponseMapper = faqResponseMapper,
            faqTrendCalculator = faqTrendCalculator,
            applicationConfig = insightsTestConfig(faq = FaqInsightsConfig(rebuildQuestionLimit = 50)),
            transactionManager = transactionManager,
        )
        // The windowed stub first: mockk matches the most recently defined one, and `any()` would
        // otherwise swallow the null call too.
        every { chatQuestionApi.countUserQuestionsForProject(projectId, any<Instant>()) } returns 400
        every { chatQuestionApi.countUserQuestionsForProject(projectId, null) } returns 900

        val preview = service.previewRebuild(projectId, listOf(1, 30))

        // The total is honest about what exists; the windows describe what would actually be
        // sent, so a client showing them is not promising something the rebuild cannot keep.
        assertEquals(900, preview.totalQuestionCount)
        assertEquals(listOf(1, 30), preview.windows.map { it.sinceDays })
        assertEquals(listOf(50, 50), preview.windows.map { it.questionCount })
    }

    @Test
    fun `a scope bound below one is rejected`() {
        // Coercing it would hide the caller's bug behind a wiped FAQ.
        assertThrows<ResponseStatusException> { FaqRebuildScope(questionLimit = 0) }
        assertThrows<ResponseStatusException> { FaqRebuildScope(sinceDays = -1) }
    }
}
