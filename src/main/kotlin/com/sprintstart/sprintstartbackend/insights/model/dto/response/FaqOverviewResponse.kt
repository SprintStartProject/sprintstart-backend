package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "Recurring-question entries for the PM FAQ dashboard, most frequent first.")
data class FaqOverviewResponse(
    @field:Schema(description = "Recurring-question entries ordered by descending occurrence count.")
    val groups: List<FaqGroupSummaryResponse>,
    @field:Schema(description = "When a question was last filed into this project's FAQ. Null when it is empty.")
    val lastAskedAt: Instant? = null,
)

@Schema(
    description = "How an entry's volume is moving, comparing the current trend window against " +
        "the one before it. Measured over the questions stored for it.",
)
enum class FaqTrend {
    @Schema(description = "Asked more often than in the previous window.")
    RISING,

    @Schema(description = "Asked about as often as in the previous window.")
    STEADY,

    @Schema(description = "Asked less often than in the previous window, or not at all in either.")
    FADING,
}

@Schema(description = "Summary of a single recurring-question entry.")
data class FaqGroupSummaryResponse(
    @field:Schema(description = "Stable identifier of the entry, used to load its details.")
    val groupId: UUID,
    @field:Schema(description = "Total number of questions assigned to this entry.")
    val count: Int,
    @field:Schema(
        description = "Short generated title naming what the entry is about. Falls back to the " +
            "representative question for entries written before titles existed.",
    )
    val title: String,
    @field:Schema(description = "Representative question, in the wording users actually ask it.")
    val question: String,
    @field:Schema(description = "Documents that most often answered questions in this entry.")
    val topDocuments: List<FaqDocumentPreviewResponse>,
    @field:Schema(description = "Questions assigned to this entry within the current trend window.")
    val recentCount: Int = 0,
    @field:Schema(description = "Whether the entry is growing, holding steady, or going quiet.")
    val trend: FaqTrend = FaqTrend.STEADY,
    @field:Schema(description = "When this entry's question was last asked.")
    val lastAskedAt: Instant? = null,
)

@Schema(description = "Minimal document reference shown in the entry overview.")
data class FaqDocumentPreviewResponse(
    @field:Schema(description = "Identifier of the document in the upstream knowledge base.")
    val id: String,
    @field:Schema(description = "Human-readable document title.")
    val title: String,
)
