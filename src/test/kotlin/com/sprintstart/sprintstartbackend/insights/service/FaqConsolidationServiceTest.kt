package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.FaqInsightsConfig
import com.sprintstart.sprintstartbackend.insights.InsightsAiClient
import com.sprintstart.sprintstartbackend.insights.insightsTestConfig
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqMerge
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
import io.mockk.verify
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
        category: String?,
        count: Int = 1,
        firstAskedAt: Instant = Instant.parse("2026-08-01T00:00:00Z"),
        lastAskedAt: Instant = Instant.parse("2026-08-01T00:00:00Z"),
    ) = FaqGroup(
        projectId = projectId,
        question = question,
        occurrenceCount = count,
        category = category,
        firstAskedAt = firstAskedAt,
        lastAskedAt = lastAskedAt,
    )

    private fun givenGroups(groups: List<FaqGroup>) {
        every { faqGroupRepository.findAllByProjectIdOrderByOccurrenceCountDesc(projectId) } returns groups
    }

    private fun givenGroupsIn(category: String, groups: List<FaqGroup>) {
        every {
            faqGroupRepository.findAllByProjectIdAndCategoryOrderByOccurrenceCountDesc(projectId, category)
        } returns groups
        every { faqGroupRepository.save(any<FaqGroup>()) } answers { firstArg() }
    }

    // ── categories ──────────────────────────────────────────────────────────

    @Test
    fun `leaves the categories alone while they fit under the ceiling`() = runTest {
        givenGroups(listOf(group("q1", "A"), group("q2", "B")))

        val renames = serviceWith(FaqInsightsConfig(maxCategories = 2)).consolidateCategories(projectId)

        assertTrue(renames.isEmpty())
        coVerify(exactly = 0) { insightsAiClient.consolidateFaqCategories(any()) }
    }

    @Test
    fun `refiles the groups of merged-away categories`() = runTest {
        val backend = group("q2", "Backend Setup")
        val frontend = group("q3", "Frontend Setup")
        givenGroups(listOf(group("q1", "Local Setup"), backend, frontend))
        coEvery { insightsAiClient.consolidateFaqCategories(any()) } returns AiFaqMergeResponse(
            merges = listOf(AiFaqMerge(into = "Local Setup", sources = listOf("Backend Setup", "Frontend Setup"))),
        )

        val renames = serviceWith(FaqInsightsConfig(maxCategories = 2)).consolidateCategories(projectId)

        assertEquals("Local Setup", renames["Backend Setup"])
        assertEquals("Local Setup", backend.category)
        assertEquals("Local Setup", frontend.category)
    }

    @Test
    fun `accepts an umbrella category the model invented`() = runTest {
        val backend = group("q1", "Backend")
        val frontend = group("q2", "Frontend")
        givenGroups(listOf(backend, frontend, group("q3", "Testing")))
        coEvery { insightsAiClient.consolidateFaqCategories(any()) } returns AiFaqMergeResponse(
            merges = listOf(AiFaqMerge(into = "Local Setup", sources = listOf("Backend", "Frontend"))),
        )

        serviceWith(FaqInsightsConfig(maxCategories = 2)).consolidateCategories(projectId)

        assertEquals("Local Setup", backend.category)
        assertEquals("Local Setup", frontend.category)
    }

    @Test
    fun `ignores a category the model invented as a source`() = runTest {
        val backend = group("q1", "Backend")
        givenGroups(listOf(backend, group("q2", "Frontend"), group("q3", "Testing")))
        coEvery { insightsAiClient.consolidateFaqCategories(any()) } returns AiFaqMergeResponse(
            merges = listOf(AiFaqMerge(into = "Local Setup", sources = listOf("Backend", "Never Existed"))),
        )

        val renames = serviceWith(FaqInsightsConfig(maxCategories = 2)).consolidateCategories(projectId)

        assertEquals(mapOf("Backend" to "Local Setup"), renames)
    }

    @Test
    fun `never merges one category into two places`() = runTest {
        givenGroups(listOf(group("q1", "A"), group("q2", "B"), group("q3", "C")))
        coEvery { insightsAiClient.consolidateFaqCategories(any()) } returns AiFaqMergeResponse(
            merges = listOf(
                AiFaqMerge(into = "A", sources = listOf("B")),
                AiFaqMerge(into = "C", sources = listOf("B")),
            ),
        )

        val renames = serviceWith(FaqInsightsConfig(maxCategories = 2)).consolidateCategories(projectId)

        assertEquals(mapOf("B" to "A"), renames)
    }

    @Test
    fun `drops a merge whose target a later merge consumes`() = runTest {
        givenGroups(listOf(group("q1", "A"), group("q2", "B"), group("q3", "C")))
        coEvery { insightsAiClient.consolidateFaqCategories(any()) } returns AiFaqMergeResponse(
            merges = listOf(
                AiFaqMerge(into = "B", sources = listOf("C")),
                AiFaqMerge(into = "A", sources = listOf("B")),
            ),
        )

        // Applying both would make the outcome depend on which ran first.
        val renames = serviceWith(FaqInsightsConfig(maxCategories = 2)).consolidateCategories(projectId)

        assertEquals(mapOf("B" to "A"), renames)
    }

    // ── groups ──────────────────────────────────────────────────────────────

    @Test
    fun `leaves a category alone while its groups fit under the ceiling`() = runTest {
        givenGroupsIn("Local Setup", listOf(group("q1", "Local Setup"), group("q2", "Local Setup")))

        serviceWith(FaqInsightsConfig(maxGroupsPerCategory = 2)).mergeGroupsIn(projectId, "Local Setup")

        coVerify(exactly = 0) { insightsAiClient.mergeFaqGroups(any()) }
    }

    @Test
    fun `folds a duplicate group into the surviving one`() = runTest {
        val target = group(
            "How do I start the backend?",
            "Local Setup",
            count = 5,
            firstAskedAt = Instant.parse("2026-08-05T00:00:00Z"),
            lastAskedAt = Instant.parse("2026-08-10T00:00:00Z"),
        )
        target.documents.add(FaqDocument(documentRef = "doc_001", title = "Setup", source = null, group = target))
        val duplicate = group(
            "how to start backend",
            "Local Setup",
            count = 2,
            firstAskedAt = Instant.parse("2026-08-01T00:00:00Z"),
            lastAskedAt = Instant.parse("2026-08-12T00:00:00Z"),
        )
        duplicate.questions.add(FaqQuestion(text = "how to start backend", group = duplicate))
        duplicate.documents.add(FaqDocument(documentRef = "doc_001", title = "Setup", source = null, group = duplicate))
        duplicate.documents.add(
            FaqDocument(documentRef = "doc_002", title = "Runbook", source = null, group = duplicate),
        )
        val other = group("q3", "Local Setup")
        givenGroupsIn("Local Setup", listOf(target, duplicate, other))
        coEvery { insightsAiClient.mergeFaqGroups(any()) } returns AiFaqMergeResponse(
            merges = listOf(AiFaqMerge(into = target.id.toString(), sources = listOf(duplicate.id.toString()))),
        )

        serviceWith(FaqInsightsConfig(maxGroupsPerCategory = 2)).mergeGroupsIn(projectId, "Local Setup")

        assertEquals(7, target.occurrenceCount)
        assertEquals(Instant.parse("2026-08-01T00:00:00Z"), target.firstAskedAt)
        assertEquals(Instant.parse("2026-08-12T00:00:00Z"), target.lastAskedAt)
        assertEquals(1, target.questions.size)
        // The duplicate must not keep pointing at the moved rows: it is about to be deleted, and
        // orphanRemoval would take them with it.
        assertTrue(duplicate.questions.isEmpty())
        // Both groups answered the same question, so they cited the same document; showing it
        // twice would be the merge's own doing.
        assertEquals(listOf("doc_001", "doc_002"), target.documents.map { it.documentRef })
    }

    @Test
    fun `deletes the groups it merged away`() = runTest {
        val target = group("How do I start the backend?", "Local Setup", count = 5)
        val duplicate = group("how to start backend", "Local Setup", count = 2)
        givenGroupsIn("Local Setup", listOf(target, duplicate, group("q3", "Local Setup")))
        coEvery { insightsAiClient.mergeFaqGroups(any()) } returns AiFaqMergeResponse(
            merges = listOf(AiFaqMerge(into = target.id.toString(), sources = listOf(duplicate.id.toString()))),
        )
        val deleted = slot<List<FaqGroup>>()
        every { faqGroupRepository.deleteAll(capture(deleted)) } answers { }

        serviceWith(FaqInsightsConfig(maxGroupsPerCategory = 2)).mergeGroupsIn(projectId, "Local Setup")

        assertEquals(listOf(duplicate.id), deleted.captured.map { it.id })
    }

    @Test
    fun `ignores a merge into a group that does not exist`() = runTest {
        val existing = group("q1", "Local Setup")
        givenGroupsIn("Local Setup", listOf(existing, group("q2", "Local Setup"), group("q3", "Local Setup")))
        coEvery { insightsAiClient.mergeFaqGroups(any()) } returns AiFaqMergeResponse(
            merges = listOf(AiFaqMerge(into = UUID.randomUUID().toString(), sources = listOf(existing.id.toString()))),
        )

        serviceWith(FaqInsightsConfig(maxGroupsPerCategory = 2)).mergeGroupsIn(projectId, "Local Setup")

        // The survivor carries the stored samples and documents, so an invented id has nothing
        // behind it to merge into.
        coVerify(exactly = 0) { faqGroupRepository.deleteAll(any<List<FaqGroup>>()) }
    }

    // ── enforceLimits ───────────────────────────────────────────────────────

    @Test
    fun `follows a category that was renamed while enforcing the limits`() = runTest {
        val backend = group("q1", "Backend Setup")
        givenGroups(listOf(group("q2", "Local Setup"), backend, group("q3", "Testing")))
        givenGroupsIn("Local Setup", listOf(group("q4", "Local Setup")))
        coEvery { insightsAiClient.consolidateFaqCategories(any()) } returns AiFaqMergeResponse(
            merges = listOf(AiFaqMerge(into = "Local Setup", sources = listOf("Backend Setup"))),
        )

        serviceWith(FaqInsightsConfig(maxCategories = 2, maxGroupsPerCategory = 5))
            .enforceLimits(projectId, "Backend Setup")

        // The question landed in a category that no longer exists; its groups moved to the
        // surviving one, which is where the group ceiling now has to be checked. Checking the
        // old name would look at nothing and miss the overflow the merge just caused.
        verify(exactly = 1) {
            faqGroupRepository.findAllByProjectIdAndCategoryOrderByOccurrenceCountDesc(projectId, "Local Setup")
        }
        verify(exactly = 0) {
            faqGroupRepository.findAllByProjectIdAndCategoryOrderByOccurrenceCountDesc(projectId, "Backend Setup")
        }
    }

    @Test
    fun `swallows a failing consolidation rather than losing the filed question`() = runTest {
        givenGroups(listOf(group("q1", "A"), group("q2", "B"), group("q3", "C")))
        coEvery { insightsAiClient.consolidateFaqCategories(any()) } throws InsightsAiException("AI service down")

        // The question that triggered this is already stored; an over-full category list is a
        // cosmetic problem next to propagating the failure.
        serviceWith(FaqInsightsConfig(maxCategories = 2)).enforceLimits(projectId, "A")
    }
}
