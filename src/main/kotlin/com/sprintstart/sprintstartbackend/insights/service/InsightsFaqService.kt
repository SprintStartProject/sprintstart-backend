package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.chat.external.ChatQuestionApi
import com.sprintstart.sprintstartbackend.insights.InsightsAiClient
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupingRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqQuestion
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDetailResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshFaqResponse
import com.sprintstart.sprintstartbackend.insights.model.mapper.AiFaqGroupMapper
import com.sprintstart.sprintstartbackend.insights.model.mapper.FaqResponseMapper
import com.sprintstart.sprintstartbackend.insights.repository.FaqGroupRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Serves the PM FAQ insights and keeps the persisted grouping cache up to date.
 *
 * Read endpoints are served entirely from the local cache. A refresh delegates the semantic
 * grouping and PII redaction to the AI service and then rebuilds the cache from the result, so the
 * dashboard reads stay fast and independent of AI latency.
 */
@Service
class InsightsFaqService(
    private val faqGroupRepository: FaqGroupRepository,
    private val insightsAiClient: InsightsAiClient,
    private val chatQuestionApi: ChatQuestionApi,
    private val aiFaqGroupMapper: AiFaqGroupMapper,
    private val faqResponseMapper: FaqResponseMapper,
) {
    /**
     * Returns all cached recurring-question groups, most frequently asked first.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving FAQ overview")
    fun getFaqOverview(): FaqOverviewResponse {
        return faqResponseMapper.toOverviewResponse(
            faqGroupRepository.findAllByOrderByOccurrenceCountDesc(),
        )
    }

    /**
     * Returns the details of a single cached group.
     *
     * @throws ResponseStatusException 404 if no group with [groupId] exists.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving FAQ group")
    fun getFaqGroup(groupId: UUID): FaqDetailResponse {
        val group = faqGroupRepository.findById(groupId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "FAQ group with id $groupId not found")
        }
        return faqResponseMapper.toDetailResponse(group)
    }

    /**
     * Recomputes the recurring-question groups via the AI service and replaces the cache.
     *
     * Collects all user questions, sends them to the stateless AI service for grouping, and stores
     * the freshly grouped result as the new cache, replacing the previous groups.
     *
     * @throws com.sprintstart.sprintstartbackend.insights.model.exceptions.InsightsAiException
     *   if the AI service does not return a grouping result.
     */
    @Tracked("Refreshing FAQ groups")
    suspend fun refreshFaqGroups(): RefreshFaqResponse {
        val questions = chatQuestionApi
            .getAllUserQuestions()
            .map { AiFaqQuestion(id = it.id.toString(), text = it.text) }

        val aiResponse = insightsAiClient.groupFaqQuestions(AiFaqGroupingRequest(questions))
        val groups = aiResponse.groups.map { aiFaqGroupMapper.toEntity(it) }

        faqGroupRepository.deleteAll()
        faqGroupRepository.saveAll(groups)

        return RefreshFaqResponse(groupCount = groups.size)
    }
}
