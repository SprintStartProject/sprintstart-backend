package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.insights.external.KnowledgeGapsRefresh
import com.sprintstart.sprintstartbackend.insights.model.dto.request.FaqRebuildScope
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshFaqResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshKnowledgeGapsResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class InsightsRefreshApiServiceTest {
    private val insightsFaqService: InsightsFaqService = mockk()
    private val knowledgeGapsService: KnowledgeGapsService = mockk()
    private val api = InsightsRefreshApiService(insightsFaqService, knowledgeGapsService)

    private val projectId = UUID.randomUUID()

    /** Another module's rebuild is the page's rebuild: the whole project, no limits. */
    @Test
    fun `refreshing the FAQ runs a full rebuild of that project and reports the groups stored`() = runTest {
        coEvery { insightsFaqService.refreshFaqGroups(projectId, FaqRebuildScope()) } returns
            RefreshFaqResponse(groupCount = 4)

        val groups = api.refreshFaq(projectId)

        assertThat(groups).isEqualTo(4)
        val fullRebuild = FaqRebuildScope(questionLimit = null, sinceDays = null)
        coVerify { insightsFaqService.refreshFaqGroups(projectId, fullRebuild) }
    }

    @Test
    fun `refreshing knowledge gaps reports gaps and components covered`() = runTest {
        coEvery { knowledgeGapsService.refreshKnowledgeGaps(projectId) } returns
            RefreshKnowledgeGapsResponse(gapCount = 2, componentCount = 7)

        val refresh = api.refreshKnowledgeGaps(projectId)

        assertThat(refresh).isEqualTo(KnowledgeGapsRefresh(gapCount = 2, componentCount = 7))
    }
}
