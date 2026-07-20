package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "Detailed view of a recurring-question group with sample questions and sources.")
data class FaqDetailResponse(
    @field:Schema(description = "Stable identifier of the group.")
    val groupId: UUID,
    @field:Schema(description = "Total number of questions assigned to this group.")
    val count: Int,
    @field:Schema(description = "Redacted sample of the questions in this group.")
    val questions: List<FaqQuestionResponse>,
    @field:Schema(description = "Documents that answered questions in this group.")
    val answeringDocuments: List<FaqDocumentResponse>,
)

@Schema(description = "A single sample question within a group.")
data class FaqQuestionResponse(
    @field:Schema(description = "Identifier of the sample question.")
    val id: UUID,
    @field:Schema(description = "Redacted question text.")
    val text: String,
)

@Schema(description = "A document that answered questions in the group.")
data class FaqDocumentResponse(
    @field:Schema(description = "Identifier of the document in the upstream knowledge base.")
    val id: String,
    @field:Schema(description = "Human-readable document title.")
    val title: String,
    @field:Schema(description = "Source system the document originates from, for example confluence. May be null.")
    val source: String?,
)
