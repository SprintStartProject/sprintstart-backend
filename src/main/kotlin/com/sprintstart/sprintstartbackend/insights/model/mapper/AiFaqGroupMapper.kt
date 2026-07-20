package com.sprintstart.sprintstartbackend.insights.model.mapper

import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroup
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqDocument
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqQuestion
import org.springframework.stereotype.Component

/**
 * Builds persistable [FaqGroup] aggregates from AI grouping results.
 *
 * The parent group is created first so its child questions and documents can hold the required
 * back-reference; children are cascaded on save.
 */
@Component
class AiFaqGroupMapper {
    fun toEntity(aiGroup: AiFaqGroup): FaqGroup {
        val group = FaqGroup(
            question = aiGroup.question,
            occurrenceCount = aiGroup.count,
        )

        aiGroup.questions.forEach { text ->
            group.questions.add(FaqQuestion(text = text, group = group))
        }

        aiGroup.documents.forEach { document ->
            group.documents.add(
                FaqDocument(
                    documentRef = document.id,
                    title = document.title,
                    source = document.source,
                    group = group,
                ),
            )
        }

        return group
    }
}
