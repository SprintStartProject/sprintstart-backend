package com.sprintstart.sprintstartbackend.insights.model.mapper

import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDetailResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDocumentPreviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqDocumentResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqGroupSummaryResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.FaqQuestionResponse
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import org.springframework.stereotype.Component

/**
 * Converts persisted FAQ groups into API response DTOs.
 *
 * The upstream document reference ([com.sprintstart.sprintstartbackend.insights.model.entity.FaqDocument.documentRef])
 * is exposed as the document id so clients can reference the real knowledge-base document.
 */
@Component
class FaqResponseMapper {
    fun toOverviewResponse(groups: List<FaqGroup>): FaqOverviewResponse {
        return FaqOverviewResponse(
            groups = groups.map { group ->
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
                )
            },
        )
    }

    fun toDetailResponse(group: FaqGroup): FaqDetailResponse {
        return FaqDetailResponse(
            groupId = group.id,
            count = group.occurrenceCount,
            questions = group.questions.map { question ->
                FaqQuestionResponse(
                    id = question.id,
                    text = question.text,
                )
            },
            answeringDocuments = group.documents.map { document ->
                FaqDocumentResponse(
                    id = document.documentRef,
                    title = document.title,
                    source = document.source,
                )
            },
        )
    }
}
