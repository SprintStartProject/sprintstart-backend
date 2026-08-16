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
            // Falls back to the redacted representative question: wordy, but it still says what
            // the entry is about, and it can never leak an unredacted name.
            title = aiGroup.title?.takeIf { it.isNotBlank() } ?: aiGroup.question,
            firstAskedAt = askedAt.minOrNull() ?: now,
            lastAskedAt = askedAt.maxOrNull() ?: now,
        )

        // One row per question the group holds, not just per sampled phrasing. The AI service
        // samples by distinct text, so an identically repeated question comes back once no matter
        // how often it was asked — counting rows off that would make a rebuilt group's trend read
        // as quieter than it is, and a verbatim repeat is precisely what a recurring question is.
        // Rows for unsampled ids carry no text; they exist to be counted.
        val sampleTexts = aiGroup.questions.associate { it.id to it.text }
        val questionIds = (aiGroup.questionIds + sampleTexts.keys).distinct()

        questionIds.forEach { questionId ->
            group.questions.add(
                FaqQuestion(
                    text = sampleTexts[questionId].orEmpty(),
                    askedAt = askedAtByQuestionId[questionId] ?: now,
                    sourceMessageId = parseUuidOrNull(questionId),
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
