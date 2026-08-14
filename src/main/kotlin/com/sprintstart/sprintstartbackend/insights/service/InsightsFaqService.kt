package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
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
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/**
 * Serves the PM FAQ insights and rebuilds the whole grouping on demand.
 *
 * Reads are served entirely from the stored groups, so the dashboard stays fast and independent of
 * AI latency. Day to day those groups are kept current by [FaqLiveUpdateService], which files each
 * question as it is asked; the refresh here re-derives everything from scratch and exists as the
 * fallback for when that structure needs to be thrown away and rebuilt.
 *
 * The refresh is the expensive path — its cost grows with every question the project has ever
 * asked — which is why it stays PM-triggered rather than automatic.
 */
@Service
class InsightsFaqService(
    private val faqGroupRepository: FaqGroupRepository,
    private val insightsAiClient: InsightsAiClient,
    private val chatQuestionApi: ChatQuestionApi,
    private val aiFaqGroupMapper: AiFaqGroupMapper,
    private val faqResponseMapper: FaqResponseMapper,
    private val faqTrendCalculator: FaqTrendCalculator,
    private val applicationConfig: ApplicationConfig,
    transactionManager: PlatformTransactionManager,
) {
    // The cache swap below deletes and re-saves in one go. Derived delete queries carry no
    // transaction of their own — unlike the inherited deleteAll() — so without this the delete
    // threw TransactionRequiredException as soon as there was anything to delete, which is why
    // the first refresh of a project appeared to work and every later one failed. Wrapping both
    // also makes the swap atomic: a failure in between would otherwise leave the panel empty.
    // @Transactional cannot be used here, the method is suspend.
    private val txTemplate = TransactionTemplate(transactionManager)

    /**
     * Returns the project's recurring-question groups, most frequently asked first, together with
     * the categories they are filed under.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving FAQ overview")
    fun getFaqOverview(projectId: UUID): FaqOverviewResponse {
        return faqResponseMapper.toOverviewResponse(
            faqGroupRepository.findAllByProjectIdOrderByOccurrenceCountDesc(projectId),
            faqTrendCalculator.statsByGroup(projectId),
        )
    }

    /**
     * Returns the details of a single group.
     *
     * @throws ResponseStatusException 404 if no group with [groupId] exists.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving FAQ group")
    fun getFaqGroup(projectId: UUID, groupId: UUID): FaqDetailResponse {
        // Scoped rather than fetched by id alone, so a group from another project reads as
        // "not found" instead of leaking its existence.
        val group = faqGroupRepository.findByIdAndProjectId(groupId, projectId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "FAQ group with id $groupId not found")
        }
        val stats = faqTrendCalculator.statsByGroup(projectId)[groupId] ?: FaqTrendCalculator.Stats.NONE
        return faqResponseMapper.toDetailResponse(group, stats)
    }

    /**
     * Recomputes the recurring-question groups via the AI service and replaces the stored ones.
     *
     * Collects all user questions, sends them to the stateless AI service for grouping and
     * categorization, and stores the freshly grouped result, replacing the previous groups.
     *
     * @throws com.sprintstart.sprintstartbackend.insights.model.exceptions.InsightsAiException
     *   if the AI service does not return a grouping result.
     */
    @Tracked("Refreshing FAQ groups")
    suspend fun refreshFaqGroups(projectId: UUID): RefreshFaqResponse {
        // Only this project's questions: the panel is per project, and mixing them would show one
        // project's questions — and the documents answering them — in another's dashboard.
        val chatQuestions = chatQuestionApi.getUserQuestionsForProject(projectId)
        val questions = chatQuestions.map { AiFaqQuestion(id = it.id.toString(), text = it.text) }
        // The AI service is stateless and returns no timestamps, so the only place a rebuilt
        // group's recency can come from is the messages the questions were asked in.
        val askedAtByQuestionId = chatQuestions.associate { it.id.toString() to it.askedAt }

        val aiResponse = insightsAiClient.groupFaqQuestions(
            AiFaqGroupingRequest(
                projectId = projectId.toString(),
                questions = questions,
                maxCategories = applicationConfig.insights.faq.maxCategories,
            ),
        )
        val groups = aiResponse.groups.map { aiFaqGroupMapper.toEntity(it, projectId, askedAtByQuestionId) }

        txTemplate.executeWithoutResult {
            faqGroupRepository.deleteAllByProjectId(projectId)
            // Rows from before insights were project-scoped are unreachable through the scoped reads.
            faqGroupRepository.deleteAllByProjectIdIsNull()
            faqGroupRepository.saveAll(groups)
        }

        return RefreshFaqResponse(groupCount = groups.size)
    }
}
