package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.chat.external.ChatQuestion
import com.sprintstart.sprintstartbackend.chat.external.ChatQuestionApi
import com.sprintstart.sprintstartbackend.insights.InsightsAiClient
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroup
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroupingRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqQuestion
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDetailResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.RefreshFaqResponse
import com.sprintstart.sprintstartbackend.insights.model.exceptions.InsightsAiException
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
     * Returns the project's recurring-question entries, most frequently asked first.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving FAQ overview")
    fun getFaqOverview(projectId: UUID): FaqOverviewResponse {
        return faqResponseMapper.toOverviewResponse(
            faqGroupRepository.findAllByProjectIdOrderByOccurrenceCountDesc(projectId),
            faqTrendCalculator.statsByGroup(projectId),
            // Counted rather than loaded: this only tells the client how much material a rebuild
            // would work on, so a PM knows what the button is about to do.
            rebuildQuestionCount = rebuildQuestionCount(projectId),
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
     * titling, and stores the freshly grouped result, replacing the previous entries.
     *
     * @throws com.sprintstart.sprintstartbackend.insights.model.exceptions.InsightsAiException
     *   if the AI service does not return a grouping result.
     */
    @Tracked("Refreshing FAQ groups")
    suspend fun refreshFaqGroups(projectId: UUID): RefreshFaqResponse {
        // Only this project's questions: the panel is per project, and mixing them would show one
        // project's questions — and the documents answering them — in another's dashboard.
        val chatQuestions = newestQuestions(projectId)
        val questions = chatQuestions.map { AiFaqQuestion(id = it.id.toString(), text = it.text) }
        // The AI service is stateless and returns no timestamps, so the only place a rebuilt
        // group's recency can come from is the messages the questions were asked in.
        val askedAtByQuestionId = chatQuestions.associate { it.id.toString() to it.askedAt }

        val aiResponse = insightsAiClient.groupFaqQuestions(
            AiFaqGroupingRequest(projectId = projectId.toString(), questions = questions),
        )
        rejectDegenerateResult(aiResponse.groups, questions.size)
        val groups = aiResponse.groups.map { aiFaqGroupMapper.toEntity(it, projectId, askedAtByQuestionId) }

        txTemplate.executeWithoutResult {
            faqGroupRepository.deleteAllByProjectId(projectId)
            // Rows from before insights were project-scoped are unreachable through the scoped reads.
            faqGroupRepository.deleteAllByProjectIdIsNull()
            faqGroupRepository.saveAll(groups)
        }

        return RefreshFaqResponse(groupCount = groups.size)
    }

    /**
     * The questions a rebuild sends, newest first and capped, then back in chronological order.
     *
     * The cap is the point: this is the one path that puts raw question text into a prompt, so it
     * is the one whose size grows without bound as a project keeps chatting. Past the limit the
     * older questions are genuinely dropped — a rebuild replaces the FAQ, so they leave the counts
     * with it. That is why the count is surfaced on the overview: it is the PM's decision to make,
     * not a surprise.
     *
     * Chronological order is restored afterwards because the AI service treats a cluster's first
     * member as its representative, and the oldest phrasing is the more established one.
     */
    private fun newestQuestions(projectId: UUID): List<ChatQuestion> =
        chatQuestionApi
            .getUserQuestionsForProject(projectId)
            .sortedByDescending { it.askedAt }
            .take(applicationConfig.insights.faq.rebuildQuestionLimit)
            .sortedBy { it.askedAt }

    private fun rebuildQuestionCount(projectId: UUID): Int =
        chatQuestionApi
            .countUserQuestionsForProject(projectId)
            .coerceAtMost(
                applicationConfig.insights.faq.rebuildQuestionLimit
                    .toLong(),
            ).toInt()

    /**
     * Refuses a rebuild result that carries the signature of the AI service's own fallback.
     *
     * When the grouping model returns something unparseable, the AI service degrades to "every
     * question is its own cluster" rather than failing. That is a reasonable default there, but
     * here it is indistinguishable from success — and applying it would delete a working FAQ and
     * replace it with one single-count entry per question. The failure would be silent, which is
     * worse than loud.
     *
     * The test is the exact fallback signature — as many groups as questions, none of them
     * grouping anything — and only above a size where a genuine result like that is implausible:
     * a project with dozens of questions and not one repeat among them has no FAQ to speak of
     * either way.
     */
    private fun rejectDegenerateResult(groups: List<AiFaqGroup>, questionCount: Int) {
        if (questionCount < DEGENERATE_RESULT_THRESHOLD) return
        if (groups.size < questionCount || groups.any { it.count > 1 }) return

        throw InsightsAiException(
            "The AI service grouped nothing: it returned $questionCount separate questions for " +
                "$questionCount inputs. The previous FAQ was kept rather than replaced with it.",
        )
    }

    private companion object {
        /**
         * Below this many questions, one entry each is a plausible genuine result rather than a
         * failed grouping, so the guard stays out of the way.
         */
        const val DEGENERATE_RESULT_THRESHOLD = 20
    }
}
