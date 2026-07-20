package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.chat.external.ChatQuestion
import com.sprintstart.sprintstartbackend.chat.external.ChatQuestionApi
import com.sprintstart.sprintstartbackend.insights.InsightsAiClient
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqDocument
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroup
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupingRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupingResponse
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqDocument
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqQuestion
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
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import java.util.UUID

class InsightsFaqServiceTest {
    private val faqGroupRepository = mockk<FaqGroupRepository>()
    private val insightsAiClient = mockk<InsightsAiClient>()
    private val chatQuestionApi = mockk<ChatQuestionApi>()
    private val aiFaqGroupMapper = AiFaqGroupMapper()
    private val faqResponseMapper = FaqResponseMapper()

    private val service = InsightsFaqService(
        faqGroupRepository = faqGroupRepository,
        insightsAiClient = insightsAiClient,
        chatQuestionApi = chatQuestionApi,
        aiFaqGroupMapper = aiFaqGroupMapper,
        faqResponseMapper = faqResponseMapper,
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
        every { faqGroupRepository.findAllByOrderByOccurrenceCountDesc() } returns listOf(group)

        val overview = service.getFaqOverview()

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
        every { faqGroupRepository.findById(group.id) } returns Optional.of(group)

        val detail = service.getFaqGroup(group.id)

        assertEquals(group.id, detail.groupId)
        assertEquals(14, detail.count)
        assertEquals("How do I get VPN access?", detail.questions.first().text)
        assertEquals("doc_001", detail.answeringDocuments.first().id)
        assertEquals("confluence", detail.answeringDocuments.first().source)
    }

    @Test
    fun `getFaqGroup throws 404 when the group does not exist`() {
        val missingId = UUID.randomUUID()
        every { faqGroupRepository.findById(missingId) } returns Optional.empty()

        val exception = assertThrows<ResponseStatusException> {
            service.getFaqGroup(missingId)
        }
        assertEquals(HttpStatus.NOT_FOUND, exception.statusCode)
    }

    @Test
    fun `refreshFaqGroups groups via the AI service and rebuilds the cache`() = runTest {
        val aiResponse = AiFaqGroupingResponse(
            groups = listOf(
                AiFaqGroup(
                    question = "How do I get VPN access?",
                    count = 14,
                    questions = listOf("How do I get VPN access?", "Can someone enable VPN for me?"),
                    documents = listOf(
                        AiFaqDocument(
                            id = "doc_001",
                            title = "VPN Setup Guide",
                            source = "confluence",
                        ),
                    ),
                ),
            ),
        )
        every { chatQuestionApi.getAllUserQuestions() } returns
            listOf(ChatQuestion(id = UUID.randomUUID(), text = "How do I get VPN access?"))
        val requestSlot = slot<AiFaqGroupingRequest>()
        coEvery { insightsAiClient.groupFaqQuestions(capture(requestSlot)) } returns aiResponse
        every { faqGroupRepository.deleteAll() } just Runs
        val savedSlot = slot<List<FaqGroup>>()
        every { faqGroupRepository.saveAll(capture(savedSlot)) } answers { savedSlot.captured.toMutableList() }

        val result = service.refreshFaqGroups()

        assertEquals(1, result.groupCount)

        val sentRequest = requestSlot.captured
        assertEquals(1, sentRequest.questions.size)
        assertEquals("How do I get VPN access?", sentRequest.questions.first().text)

        val persisted = savedSlot.captured.first()
        assertEquals("How do I get VPN access?", persisted.question)
        assertEquals(14, persisted.occurrenceCount)
        assertEquals(2, persisted.questions.size)
        assertEquals("doc_001", persisted.documents.first().documentRef)

        coVerify(exactly = 1) { insightsAiClient.groupFaqQuestions(any()) }
        verifyOrder {
            faqGroupRepository.deleteAll()
            faqGroupRepository.saveAll(any<List<FaqGroup>>())
        }
    }

    @Test
    fun `refreshFaqGroups clears the cache even when the AI returns no groups`() = runTest {
        every { chatQuestionApi.getAllUserQuestions() } returns emptyList()
        coEvery { insightsAiClient.groupFaqQuestions(any()) } returns AiFaqGroupingResponse(groups = emptyList())
        every { faqGroupRepository.deleteAll() } just Runs
        every { faqGroupRepository.saveAll(any<List<FaqGroup>>()) } answers { mutableListOf() }

        val result = service.refreshFaqGroups()

        assertEquals(0, result.groupCount)
        verify(exactly = 1) { faqGroupRepository.deleteAll() }
    }
}
