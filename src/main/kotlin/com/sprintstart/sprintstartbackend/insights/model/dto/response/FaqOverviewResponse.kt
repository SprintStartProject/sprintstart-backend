package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "Recurring-question groups for the PM FAQ dashboard, most frequent first.")
data class FaqOverviewResponse(
    @field:Schema(description = "Recurring-question groups ordered by descending occurrence count.")
    val groups: List<FaqGroupSummaryResponse>,
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
)

@Schema(description = "Minimal document reference shown in the group overview.")
data class FaqDocumentPreviewResponse(
    @field:Schema(description = "Identifier of the document in the upstream knowledge base.")
    val id: String,
    @field:Schema(description = "Human-readable document title.")
    val title: String,
)
