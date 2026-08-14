package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "Recurring-question groups for the PM FAQ dashboard, most frequent first.")
data class FaqOverviewResponse(
    @field:Schema(description = "Recurring-question groups ordered by descending occurrence count.")
    val groups: List<FaqGroupSummaryResponse>,
    @field:Schema(
        description = "Topic categories the groups are filed under, most active first. " +
            "Groups from before categories existed are absent here and carry a null category.",
    )
    val categories: List<FaqCategoryResponse> = emptyList(),
    @field:Schema(description = "When a question was last filed into this project's FAQ. Null when it is empty.")
    val lastAskedAt: Instant? = null,
)

@Schema(
    description = "How a group's or category's volume is moving, comparing the current trend " +
        "window against the one before it. Measured over the questions stored for it.",
)
enum class FaqTrend {
    @Schema(description = "Asked more often than in the previous window.")
    RISING,

    @Schema(description = "Asked about as often as in the previous window.")
    STEADY,

    @Schema(description = "Asked less often than in the previous window, or not at all in either.")
    FADING,
}

@Schema(description = "A topic category grouping several recurring-question groups.")
data class FaqCategoryResponse(
    @field:Schema(description = "Category name, also used to group the returned groups.")
    val name: String,
    @field:Schema(description = "Number of recurring-question groups in this category.")
    val groupCount: Int,
    @field:Schema(description = "Total number of questions asked across this category's groups.")
    val questionCount: Int,
    @field:Schema(description = "Questions asked in this category within the current trend window.")
    val recentQuestionCount: Int,
    @field:Schema(description = "Whether the category is growing, holding steady, or going quiet.")
    val trend: FaqTrend,
    @field:Schema(description = "When a question in this category was last asked.")
    val lastAskedAt: Instant,
)

@Schema(description = "Summary of a single recurring-question group.")
data class FaqGroupSummaryResponse(
    @field:Schema(description = "Stable identifier of the group, used to load its details.")
    val groupId: UUID,
    @field:Schema(description = "Total number of questions assigned to this group.")
    val count: Int,
    @field:Schema(description = "Representative question describing the group.")
    val question: String,
    @field:Schema(description = "Documents that most often answered questions in this group.")
    val topDocuments: List<FaqDocumentPreviewResponse>,
    @field:Schema(description = "Topic category this group belongs to. Null for groups predating categories.")
    val category: String? = null,
    @field:Schema(description = "Questions assigned to this group within the current trend window.")
    val recentCount: Int = 0,
    @field:Schema(description = "Whether the group is growing, holding steady, or going quiet.")
    val trend: FaqTrend = FaqTrend.STEADY,
    @field:Schema(description = "When this group's question was last asked.")
    val lastAskedAt: Instant? = null,
)

@Schema(description = "Minimal document reference shown in the group overview.")
data class FaqDocumentPreviewResponse(
    @field:Schema(description = "Identifier of the document in the upstream knowledge base.")
    val id: String,
    @field:Schema(description = "Human-readable document title.")
    val title: String,
)
