package com.sprintstart.sprintstartbackend.insights.model.mapper

import com.sprintstart.sprintstartbackend.ApplicationConfig
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
 * Converts persisted FAQ entries into API response DTOs.
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
        rebuildQuestionCount: Int,
    ): FaqOverviewResponse {
        return FaqOverviewResponse(
            groups = groups.map { group ->
                val stats = statsByGroup[group.id] ?: FaqTrendCalculator.Stats.NONE
                FaqGroupSummaryResponse(
                    groupId = group.id,
                    count = group.occurrenceCount,
                    title = group.displayTitle,
                    question = group.question,
                    topDocuments = group.documents.map { document ->
                        FaqDocumentPreviewResponse(
                            id = document.documentRef,
                            title = document.title,
                        )
                    },
                    recentCount = stats.recentCount,
                    trend = stats.trend,
                    lastAskedAt = group.lastAskedAt,
                )
            },
            lastAskedAt = groups.maxOfOrNull { it.lastAskedAt },
            rebuildQuestionCount = rebuildQuestionCount,
            rebuildQuestionLimit = applicationConfig.insights.faq.rebuildQuestionLimit,
        )
    }

    fun toDetailResponse(group: FaqGroup, stats: FaqTrendCalculator.Stats): FaqDetailResponse {
        return FaqDetailResponse(
            groupId = group.id,
            count = group.occurrenceCount,
            title = group.displayTitle,
            question = group.question,
            // Newest first and capped: a long-lived entry accumulates one row per ask, and a PM
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
            recentCount = stats.recentCount,
            trend = stats.trend,
            firstAskedAt = group.firstAskedAt,
            lastAskedAt = group.lastAskedAt,
        )
    }
}

/**
 * The title to show, falling back to the representative question.
 *
 * Entries written before titles existed have none, and a client rendering an empty headline would
 * be worse than a wordy one — the question at least says what the entry is about.
 */
private val FaqGroup.displayTitle: String
    get() = title?.takeIf { it.isNotBlank() } ?: question
