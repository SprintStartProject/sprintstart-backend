package com.sprintstart.sprintstartbackend.insights.model.mapper

import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqGroup
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqDocument
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqQuestion
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

/**
 * Builds persistable [FaqGroup] aggregates from AI grouping results.
 *
 * The parent group is created first so its child questions and documents can hold the required
 * back-reference; children are cascaded on save.
 */
@Component
class AiFaqGroupMapper {
    /**
     * @param askedAtByQuestionId when each question was asked, keyed by the id it was sent to the
     * AI service under. The service holds no history and returns none, so a group's recency can
     * only be recovered here — without it every rebuilt group would look equally fresh and the
     * panel would lose its sense of which topics are actually active.
     */
    fun toEntity(
        aiGroup: AiFaqGroup,
        projectId: UUID,
        askedAtByQuestionId: Map<String, Instant>,
    ): FaqGroup {
        val askedAt = aiGroup.questionIds.mapNotNull { askedAtByQuestionId[it] }
        val now = Instant.now()

        val group = FaqGroup(
            projectId = projectId,
            question = aiGroup.question,
            occurrenceCount = aiGroup.count,
            category = aiGroup.category?.takeIf { it.isNotBlank() },
            firstAskedAt = askedAt.minOrNull() ?: now,
            lastAskedAt = askedAt.maxOrNull() ?: now,
        )

        aiGroup.questions.forEach { question ->
            val messageId = parseUuidOrNull(question.id)
            group.questions.add(
                FaqQuestion(
                    text = question.text,
                    askedAt = askedAtByQuestionId[question.id] ?: now,
                    sourceMessageId = messageId,
                    group = group,
                ),
            )
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

/**
 * Parses [value] as a [UUID], or null if it isn't one.
 *
 * The question ids handed to the AI service are chat message ids, but nothing stops it from
 * returning something else. A malformed one costs the group its idempotence marker; it must not
 * cost the whole refresh.
 */
private fun parseUuidOrNull(value: String): UUID? =
    try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
    }
