package com.sprintstart.sprintstartbackend.insights.model.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.util.UUID

@Schema(description = "Detailed view of a recurring-question entry with its phrasings and sources.")
data class FaqDetailResponse(
    @field:Schema(description = "Stable identifier of the entry.")
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
    @field:Schema(
        description = "The distinct phrasings this entry was asked in, most recently asked first. " +
            "Repeats are folded into one item carrying its occurrence count, so a question asked " +
            "the same way ten times is one line rather than ten.",
    )
    val questions: List<FaqQuestionResponse>,
    @field:Schema(description = "Documents that answered questions in this entry.")
    val answeringDocuments: List<FaqDocumentResponse>,
    @field:Schema(description = "Questions assigned to this entry within the current trend window.")
    val recentCount: Int = 0,
    @field:Schema(description = "Whether the entry is growing, holding steady, or going quiet.")
    val trend: FaqTrend = FaqTrend.STEADY,
    @field:Schema(description = "When this entry's question was first asked.")
    val firstAskedAt: Instant? = null,
    @field:Schema(description = "When this entry's question was last asked.")
    val lastAskedAt: Instant? = null,
)

@Schema(description = "One distinct phrasing an entry was asked in.")
data class FaqQuestionResponse(
    @field:Schema(description = "Identifier of the most recent ask with this phrasing.")
    val id: UUID,
    @field:Schema(description = "Redacted question text.")
    val text: String,
    @field:Schema(description = "When this phrasing was last asked.")
    val askedAt: Instant? = null,
    @field:Schema(description = "How often the entry was asked in exactly this wording.")
    val occurrences: Int = 1,
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
