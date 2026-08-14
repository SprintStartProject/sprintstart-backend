package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.FaqInsightsConfig
import com.sprintstart.sprintstartbackend.insights.InsightsAiClient
import com.sprintstart.sprintstartbackend.insights.insightsTestConfig
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqMerge
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqMergeGroupsRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqMergeResponse
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqDocument
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqQuestion
import com.sprintstart.sprintstartbackend.insights.model.exceptions.InsightsAiException
import com.sprintstart.sprintstartbackend.insights.repository.FaqGroupRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.transaction.PlatformTransactionManager
import java.time.Instant
import java.util.UUID

class FaqConsolidationServiceTest {
    private val projectId: UUID = UUID.randomUUID()

    private val faqGroupRepository = mockk<FaqGroupRepository>(relaxed = true)
    private val insightsAiClient = mockk<InsightsAiClient>()

    // Relaxed: TransactionTemplate only needs a manager to hand it a status; the callback
    // runs inline either way.
    private val transactionManager = mockk<PlatformTransactionManager>(relaxed = true)

    private fun serviceWith(faq: FaqInsightsConfig = FaqInsightsConfig()) = FaqConsolidationService(
        faqGroupRepository = faqGroupRepository,
        insightsAiClient = insightsAiClient,
        applicationConfig = insightsTestConfig(faq = faq),
        transactionManager = transactionManager,
    )

    private fun group(
        question: String,
        title: String? = null,
        count: Int = 1,
        firstAskedAt: Instant = Instant.parse("2026-08-01T00:00:00Z"),
        lastAskedAt: Instant = Instant.parse("2026-08-01T00:00:00Z"),
    ) = FaqGroup(
        projectId = projectId,
        question = question,
        occurrenceCount = count,
        title = title,
        firstAskedAt = firstAskedAt,
        lastAskedAt = lastAskedAt,
    )

    private fun givenGroups(groups: List<FaqGroup>) {
        every { faqGroupRepository.findAllByProjectIdOrderByOccurrenceCountDesc(projectId) } returns groups
        every { faqGroupRepository.save(any<FaqGroup>()) } answers { firstArg() }
    }

    @Test
    fun `leaves the FAQ alone while it fits under the ceiling`() = runTest {
        givenGroups(listOf(group("q1"), group("q2")))

        serviceWith(FaqInsightsConfig(maxGroups = 2)).mergeDuplicates(projectId)

        coVerify(exactly = 0) { insightsAiClient.mergeFaqGroups(any()) }
    }

    @Test
    fun `sends each entry's title and wording to judge duplicates by`() = runTest {
        givenGroups(
            listOf(
                group("How do I start the backend?", title = "Starting the backend locally", count = 5),
                group("how to start backend", title = "Starting the backend", count = 2),
                group("How do I get VPN access?", title = "Getting VPN access"),
            ),
        )
        val request = slot<AiFaqMergeGroupsRequest>()
        coEvery { insightsAiClient.mergeFaqGroups(capture(request)) } returns AiFaqMergeResponse()

        serviceWith(FaqInsightsConfig(maxGroups = 2)).mergeDuplicates(projectId)

        assertEquals(2, request.captured.targetMax)
        assertTrue(request.captured.groups.all { it.title.isNotBlank() && it.question.isNotBlank() })
    }

    @Test
    fun `falls back to the question as the title for entries that have none`() = runTest {
        givenGroups(listOf(group("How do I deploy?"), group("q2"), group("q3")))
        val request = slot<AiFaqMergeGroupsRequest>()
        coEvery { insightsAiClient.mergeFaqGroups(capture(request)) } returns AiFaqMergeResponse()

        serviceWith(FaqInsightsConfig(maxGroups = 2)).mergeDuplicates(projectId)

        // The model needs something to read; an empty title would tell it nothing.
        assertTrue(request.captured.groups.any { it.title == "How do I deploy?" })
    }

    @Test
    fun `folds a duplicate entry into the surviving one`() = runTest {
        val target = group(
            "How do I start the backend?",
            title = "Starting the backend locally",
            count = 5,
            firstAskedAt = Instant.parse("2026-08-05T00:00:00Z"),
            lastAskedAt = Instant.parse("2026-08-10T00:00:00Z"),
        )
        target.documents.add(FaqDocument(documentRef = "doc_001", title = "Setup", source = null, group = target))
        val duplicate = group(
            "how to start backend",
            title = "Starting the backend",
            count = 2,
            firstAskedAt = Instant.parse("2026-08-01T00:00:00Z"),
            lastAskedAt = Instant.parse("2026-08-12T00:00:00Z"),
        )
        duplicate.questions.add(FaqQuestion(text = "how to start backend", group = duplicate))
        duplicate.documents.add(FaqDocument(documentRef = "doc_001", title = "Setup", source = null, group = duplicate))
        duplicate.documents.add(
            FaqDocument(documentRef = "doc_002", title = "Runbook", source = null, group = duplicate),
        )
        givenGroups(listOf(target, duplicate, group("q3")))
        coEvery { insightsAiClient.mergeFaqGroups(any()) } returns AiFaqMergeResponse(
            merges = listOf(AiFaqMerge(into = target.id.toString(), sources = listOf(duplicate.id.toString()))),
        )

        serviceWith(FaqInsightsConfig(maxGroups = 2)).mergeDuplicates(projectId)

        assertEquals(7, target.occurrenceCount)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), target.firstAskedAt)
        assertEquals(Instant.parse("2026-08-12T00:00:00Z"), target.lastAskedAt)
        assertEquals(1, target.questions.size)
        // The survivor keeps its own title — it is the wording users actually use.
        assertEquals("Starting the backend locally", target.title)
        // The duplicate must not keep pointing at the moved rows: it is about to be deleted, and
        // orphanRemoval would take them with it.
        assertTrue(duplicate.questions.isEmpty())
        // Both entries answered the same question, so they cited the same document; showing it
        // twice would be the merge's own doing.
        assertEquals(listOf("doc_001", "doc_002"), target.documents.map { it.documentRef })
    }

    @Test
    fun `deletes the entries it merged away`() = runTest {
        val target = group("How do I start the backend?", count = 5)
        val duplicate = group("how to start backend", count = 2)
        givenGroups(listOf(target, duplicate, group("q3")))
        coEvery { insightsAiClient.mergeFaqGroups(any()) } returns AiFaqMergeResponse(
            merges = listOf(AiFaqMerge(into = target.id.toString(), sources = listOf(duplicate.id.toString()))),
        )
        val deleted = slot<List<FaqGroup>>()
        every { faqGroupRepository.deleteAll(capture(deleted)) } answers { }

        serviceWith(FaqInsightsConfig(maxGroups = 2)).mergeDuplicates(projectId)

        assertEquals(listOf(duplicate.id), deleted.captured.map { it.id })
    }

    @Test
    fun `ignores a merge into an entry that does not exist`() = runTest {
        val existing = group("q1")
        givenGroups(listOf(existing, group("q2"), group("q3")))
        coEvery { insightsAiClient.mergeFaqGroups(any()) } returns AiFaqMergeResponse(
            merges = listOf(AiFaqMerge(into = UUID.randomUUID().toString(), sources = listOf(existing.id.toString()))),
        )

        serviceWith(FaqInsightsConfig(maxGroups = 2)).mergeDuplicates(projectId)

        // The survivor carries the stored samples, title and documents, so an invented id has
        // nothing behind it to merge into.
        coVerify(exactly = 0) { faqGroupRepository.deleteAll(any<List<FaqGroup>>()) }
    }

    @Test
    fun `never merges one entry into two places`() = runTest {
        val a = group("q1")
        val b = group("q2")
        val c = group("q3")
        givenGroups(listOf(a, b, c, group("q4")))
        coEvery { insightsAiClient.mergeFaqGroups(any()) } returns AiFaqMergeResponse(
            merges = listOf(
                AiFaqMerge(into = a.id.toString(), sources = listOf(b.id.toString())),
                AiFaqMerge(into = c.id.toString(), sources = listOf(b.id.toString())),
            ),
        )
        val deleted = mutableListOf<List<FaqGroup>>()
        every { faqGroupRepository.deleteAll(capture(deleted)) } answers { }

        serviceWith(FaqInsightsConfig(maxGroups = 2)).mergeDuplicates(projectId)

        assertEquals(listOf(listOf(b.id)), deleted.map { batch -> batch.map { it.id } })
    }

    @Test
    fun `drops a merge whose target a later merge consumes`() = runTest {
        val a = group("q1")
        val b = group("q2")
        val c = group("q3")
        givenGroups(listOf(a, b, c, group("q4")))
        coEvery { insightsAiClient.mergeFaqGroups(any()) } returns AiFaqMergeResponse(
            merges = listOf(
                AiFaqMerge(into = b.id.toString(), sources = listOf(c.id.toString())),
                AiFaqMerge(into = a.id.toString(), sources = listOf(b.id.toString())),
            ),
        )
        val deleted = mutableListOf<List<FaqGroup>>()
        every { faqGroupRepository.deleteAll(capture(deleted)) } answers { }

        // Applying both would make the outcome depend on which ran first.
        serviceWith(FaqInsightsConfig(maxGroups = 2)).mergeDuplicates(projectId)

        assertEquals(listOf(listOf(b.id)), deleted.map { batch -> batch.map { it.id } })
    }

    @Test
    fun `swallows a failing consolidation rather than losing the filed question`() = runTest {
        givenGroups(listOf(group("q1"), group("q2"), group("q3")))
        coEvery { insightsAiClient.mergeFaqGroups(any()) } throws InsightsAiException("AI service down")

        // The question that triggered this is already stored; an over-full list is a cosmetic
        // problem next to propagating the failure.
        serviceWith(FaqInsightsConfig(maxGroups = 2)).enforceGroupLimit(projectId)
    }
}
