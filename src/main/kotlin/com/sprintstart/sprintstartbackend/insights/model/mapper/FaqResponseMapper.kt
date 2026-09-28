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
        questionCount: Int,
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
            questionCount = questionCount,
            rebuildQuestionLimit = applicationConfig.insights.faq.rebuildQuestionLimit,
        )
    }

    fun toDetailResponse(group: FaqGroup, stats: FaqTrendCalculator.Stats): FaqDetailResponse {
        return FaqDetailResponse(
            groupId = group.id,
            count = group.occurrenceCount,
            title = group.displayTitle,
            question = group.question,
            questions = toPhrasings(group),
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

    /**
     * The distinct phrasings an entry was asked in, most recently asked first and capped.
     *
     * Identical wordings are folded into one item with a count rather than listed separately: a
     * question asked the same way ten times is the *same* phrasing ten times, and ten copies of
     * one line would push the genuinely different wordings — the interesting ones — past the cap.
     *
     * Text-less rows are counted elsewhere but never shown; they record that an ask happened, and
     * a rebuild produces one for every question whose wording it did not carry back.
     */
    private fun toPhrasings(group: FaqGroup): List<FaqQuestionResponse> =
        group.questions
            .filter { it.text.isNotBlank() }
            .groupBy { it.text }
            .map { (text, asks) ->
                val newest = asks.maxBy { it.askedAt }
                FaqQuestionResponse(
                    id = newest.id,
                    text = text,
                    askedAt = newest.askedAt,
                    occurrences = asks.size,
                )
            }.sortedByDescending { it.askedAt }
            .take(applicationConfig.insights.faq.sampleQuestions)
}

/**
 * The title to show, falling back to the representative question.
 *
 * Entries written before titles existed have none, and a client rendering an empty headline would
 * be worse than a wordy one — the question at least says what the entry is about.
 */
private val FaqGroup.displayTitle: String
    get() = title?.takeIf { it.isNotBlank() } ?: question
