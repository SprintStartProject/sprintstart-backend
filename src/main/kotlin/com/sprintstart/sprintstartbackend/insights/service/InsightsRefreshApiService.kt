package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.insights.external.InsightsRefreshApi
import com.sprintstart.sprintstartbackend.insights.external.KnowledgeGapsRefresh
import com.sprintstart.sprintstartbackend.insights.model.dto.request.FaqRebuildScope
import com.sprintstart.sprintstartbackend.insights.model.exceptions.InsightsAiException
import com.sprintstart.sprintstartbackend.insights.model.exceptions.KnowledgeGapsAiException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Serves [InsightsRefreshApi] from the services the insights pages already use, so a rebuild started
 * from another module is exactly the rebuild a manager starts from the page.
 *
 * The AI failures those services throw are this module's own exception types, and their messages carry
 * the AI service's HTTP status and body. Neither belongs in front of whoever asked for the rebuild, so
 * they leave this API as a [ResponseStatusException] with a sentence a person can read, and the detail
 * goes to the log.
 */
@Service
internal class InsightsRefreshApiService(
    private val insightsFaqService: InsightsFaqService,
    private val knowledgeGapsService: KnowledgeGapsService,
) : InsightsRefreshApi {
    private val logger = LoggerFactory.getLogger(javaClass)

    /** A full rebuild: no question limit and no age cut-off, the page's own default. */
    override suspend fun refreshFaq(projectId: UUID): Int =
        try {
            insightsFaqService.refreshFaqGroups(projectId, FaqRebuildScope()).groupCount
        } catch (e: InsightsAiException) {
            logger.warn("FAQ refresh for project {} failed: {}", projectId, e.message)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, FAQ_NOT_REBUILT, e)
        }

    override suspend fun refreshKnowledgeGaps(projectId: UUID): KnowledgeGapsRefresh {
        val refreshed = try {
            knowledgeGapsService.refreshKnowledgeGaps(projectId)
        } catch (e: KnowledgeGapsAiException) {
            logger.warn("Knowledge-gap rescan for project {} failed: {}", projectId, e.message)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, GAPS_NOT_RESCANNED, e)
        }
        return KnowledgeGapsRefresh(gapCount = refreshed.gapCount, componentCount = refreshed.componentCount)
    }

    private companion object {
        const val FAQ_NOT_REBUILT = "The AI service could not rebuild the FAQ just now. Try again later."
        const val GAPS_NOT_RESCANNED =
            "The AI service could not rescan for documentation gaps just now. Try again later."
    }
}
