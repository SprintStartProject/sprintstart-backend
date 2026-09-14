package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.insights.external.InsightsRefreshApi
import com.sprintstart.sprintstartbackend.insights.external.KnowledgeGapsRefresh
import com.sprintstart.sprintstartbackend.insights.model.dto.request.FaqRebuildScope
import org.springframework.stereotype.Service
import java.util.UUID

/**
 * Serves [InsightsRefreshApi] from the services the insights pages already use, so a rebuild started
 * from another module is exactly the rebuild a manager starts from the page.
 */
@Service
internal class InsightsRefreshApiService(
    private val insightsFaqService: InsightsFaqService,
    private val knowledgeGapsService: KnowledgeGapsService,
) : InsightsRefreshApi {
    /** A full rebuild: no question limit and no age cut-off, the page's own default. */
    override suspend fun refreshFaq(projectId: UUID): Int =
        insightsFaqService.refreshFaqGroups(projectId, FaqRebuildScope()).groupCount

    override suspend fun refreshKnowledgeGaps(projectId: UUID): KnowledgeGapsRefresh {
        val refreshed = knowledgeGapsService.refreshKnowledgeGaps(projectId)
        return KnowledgeGapsRefresh(gapCount = refreshed.gapCount, componentCount = refreshed.componentCount)
    }
}
