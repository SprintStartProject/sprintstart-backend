package com.sprintstart.sprintstartbackend.insights.model.mapper

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqCategoryResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDetailResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDocumentPreviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDocumentResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqGroupSummaryResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqQuestionResponse
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import com.sprintstart.sprintstartbackend.insights.service.FaqTrendCalculator
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Converts persisted FAQ groups into API response DTOs.
 *
 * The upstream document reference ([com.sprintstart.sprintstartbackend.insights.model.entity.FaqDocument.documentRef])
 * is exposed as the document id so clients can reference the real knowledge-base document.
 */
@Component
class FaqResponseMapper(
    private val applicationConfig: ApplicationConfig,
) {
    fun toOverviewResponse(
        groups: List<FaqGroup>,
        statsByGroup: Map<UUID, FaqTrendCalculator.Stats>,
    ): FaqOverviewResponse {
        return FaqOverviewResponse(
            groups = groups.map { group ->
                val stats = statsByGroup[group.id] ?: FaqTrendCalculator.Stats.NONE
                FaqGroupSummaryResponse(
                    groupId = group.id,
                    count = group.occurrenceCount,
                    question = group.question,
                    topDocuments = group.documents.map { document ->
                        FaqDocumentPreviewResponse(
                            id = document.documentRef,
                            title = document.title,
                        )
                    },
                    category = group.category,
                    recentCount = stats.recentCount,
                    trend = stats.trend,
                    lastAskedAt = group.lastAskedAt,
                )
            },
            categories = toCategories(groups, statsByGroup),
            lastAskedAt = groups.maxOfOrNull { it.lastAskedAt },
        )
    }

    fun toDetailResponse(group: FaqGroup, stats: FaqTrendCalculator.Stats): FaqDetailResponse {
        return FaqDetailResponse(
            groupId = group.id,
            count = group.occurrenceCount,
            // Newest first and capped: a long-lived group accumulates one row per ask, and a PM
            // opening it wants to see how the question is being phrased now, not scroll a log.
            questions = group.questions
                .sortedByDescending { it.askedAt }
                .take(applicationConfig.insights.faq.sampleQuestions)
                .map { question ->
                    FaqQuestionResponse(
                        id = question.id,
                        text = question.text,
                        askedAt = question.askedAt,
                    )
                },
            answeringDocuments = group.documents.map { document ->
                FaqDocumentResponse(
                    id = document.documentRef,
                    title = document.title,
                    source = document.source,
                )
            },
            category = group.category,
            recentCount = stats.recentCount,
            trend = stats.trend,
            firstAskedAt = group.firstAskedAt,
            lastAskedAt = group.lastAskedAt,
        )
    }

    /**
     * Rolls the groups up into their categories, most active first.
     *
     * Ordered by recent volume rather than all-time count, which is what makes a category that is
     * picking up surface above one that was busy months ago and has gone quiet since — the
     * "growing topics surface, stale ones fade" behaviour the panel is for.
     */
    private fun toCategories(
        groups: List<FaqGroup>,
        statsByGroup: Map<UUID, FaqTrendCalculator.Stats>,
    ): List<FaqCategoryResponse> =
        groups
            .mapNotNull { group -> group.category?.let { it to group } }
            .groupBy({ it.first }, { it.second })
            .map { (name, groupsInCategory) ->
                val stats = groupsInCategory
                    .map { statsByGroup[it.id] ?: FaqTrendCalculator.Stats.NONE }
                    .fold(FaqTrendCalculator.Stats.NONE, FaqTrendCalculator.Stats::plus)
                FaqCategoryResponse(
                    name = name,
                    groupCount = groupsInCategory.size,
                    questionCount = groupsInCategory.sumOf { it.occurrenceCount },
                    recentQuestionCount = stats.recentCount,
                    trend = stats.trend,
                    lastAskedAt = groupsInCategory.maxOf { it.lastAskedAt },
                )
            }.sortedWith(
                compareByDescending<FaqCategoryResponse> { it.recentQuestionCount }
                    .thenByDescending { it.questionCount },
            )
}
