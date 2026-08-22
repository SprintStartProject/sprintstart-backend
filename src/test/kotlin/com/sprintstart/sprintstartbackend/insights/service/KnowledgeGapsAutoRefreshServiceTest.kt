package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.KnowledgeGapsInsightsConfig
import com.sprintstart.sprintstartbackend.insights.insightsTestConfig
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshKnowledgeGapsResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class KnowledgeGapsAutoRefreshServiceTest {
    private val projectId: UUID = UUID.randomUUID()
    private val testScope = TestScope()

    private val knowledgeGapsService = mockk<KnowledgeGapsService>()
    private val refreshTracker = InsightsRefreshTracker()

    private fun serviceWith(
        config: KnowledgeGapsInsightsConfig = KnowledgeGapsInsightsConfig(debounceSeconds = 60),
    ) = KnowledgeGapsAutoRefreshService(
        knowledgeGapsService = knowledgeGapsService,
        refreshTracker = refreshTracker,
        applicationConfig = insightsTestConfig(knowledgeGaps = config),
        applicationScope = testScope,
    )

    private fun givenRefreshSucceeds() {
        coEvery { knowledgeGapsService.refreshKnowledgeGaps(projectId) } returns
            RefreshKnowledgeGapsResponse(gapCount = 2)
    }

    @Test
    fun `rescans once the debounce window has passed`() {
        givenRefreshSucceeds()

        serviceWith().scheduleRefresh(projectId)
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { knowledgeGapsService.refreshKnowledgeGaps(projectId) }
    }

    @Test
    fun `waits for the debounce window instead of rescanning immediately`() {
        givenRefreshSucceeds()

        serviceWith().scheduleRefresh(projectId)
        testScope.advanceTimeBy(30.seconds)

        coVerify(exactly = 0) { knowledgeGapsService.refreshKnowledgeGaps(projectId) }
    }

    @Test
    fun `a burst of ingestion runs costs one rescan`() {
        givenRefreshSucceeds()
        val service = serviceWith()

        // Connecting a repository produces a run per resource type; each one arriving would
        // otherwise trigger its own full scan over the same corpus.
        repeat(5) {
            service.scheduleRefresh(projectId)
            testScope.advanceTimeBy(10.seconds)
        }
        testScope.advanceUntilIdle()

        coVerify(exactly = 1) { knowledgeGapsService.refreshKnowledgeGaps(projectId) }
    }

    @Test
    fun `does nothing when automatic rescans are switched off`() {
        serviceWith(KnowledgeGapsInsightsConfig(autoRefresh = false)).scheduleRefresh(projectId)
        testScope.advanceUntilIdle()

        coVerify(exactly = 0) { knowledgeGapsService.refreshKnowledgeGaps(projectId) }
        assertFalse(refreshTracker.isRefreshingKnowledgeGaps(projectId))
    }

    @Test
    fun `reports the project as refreshing until the rescan finishes`() {
        givenRefreshSucceeds()

        serviceWith().scheduleRefresh(projectId)
        assertTrue(refreshTracker.isRefreshingKnowledgeGaps(projectId))

        testScope.advanceUntilIdle()
        assertFalse(refreshTracker.isRefreshingKnowledgeGaps(projectId))
    }

    @Test
    fun `stops reporting a refresh that failed`() {
        coEvery { knowledgeGapsService.refreshKnowledgeGaps(projectId) } throws IllegalStateException("AI down")

        serviceWith().scheduleRefresh(projectId)
        testScope.advanceUntilIdle()

        // A failed rescan leaves the previous gaps in place; the panel must stop claiming it is
        // still working on them.
        assertFalse(refreshTracker.isRefreshingKnowledgeGaps(projectId))
    }
}
