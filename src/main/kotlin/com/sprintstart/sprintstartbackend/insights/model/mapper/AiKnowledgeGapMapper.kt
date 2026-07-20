package com.sprintstart.sprintstartbackend.insights.model.mapper

import com.sprintstart.sprintstartbackend.insights.model.ai.AiKnowledgeGap
import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGap
import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGapSeverity
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Builds persistable [KnowledgeGap] entities from AI classification results.
 *
 * The AI severity string is mapped to [KnowledgeGapSeverity] and the ISO-8601 timestamp to an
 * [Instant].
 */
@Component
class AiKnowledgeGapMapper {
    fun toEntity(aiGap: AiKnowledgeGap): KnowledgeGap {
        val gap = KnowledgeGap(
            component = aiGap.component,
            lastUpdated = Instant.parse(aiGap.lastUpdated),
            severity = KnowledgeGapSeverity.fromApiValue(aiGap.severity),
        )

        gap.missingTypes.addAll(aiGap.missingTypes)
        gap.presentTypes.addAll(aiGap.presentTypes)

        return gap
    }
}
