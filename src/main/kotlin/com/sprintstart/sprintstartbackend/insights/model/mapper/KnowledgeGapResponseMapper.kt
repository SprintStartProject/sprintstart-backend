package com.sprintstart.sprintstartbackend.insights.model.mapper

import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapOwnerResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapResponse
import com.sprintstart.sprintstartbackend.insights.model.dto.response.KnowledgeGapsOverviewResponse
import com.sprintstart.sprintstartbackend.insights.model.entity.KnowledgeGap
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Converts persisted knowledge gaps into API response DTOs.
 *
 * The severity enum is rendered as its lowercase API value. Owners and the first-ingested timestamp
 * are not stored on the gap; the caller resolves them per component and passes them in.
 */
@Component
class KnowledgeGapResponseMapper {
    fun toOverviewResponse(
        gaps: List<KnowledgeGap>,
        ownersByComponent: Map<String, List<KnowledgeGapOwnerResponse>>,
        firstIngestedByComponent: Map<String, Instant>,
        refreshing: Boolean = false,
    ): KnowledgeGapsOverviewResponse {
        return KnowledgeGapsOverviewResponse(
            gaps = gaps.map {
                toResponse(
                    it,
                    ownersByComponent[it.component] ?: emptyList(),
                    firstIngestedByComponent[it.component],
                )
            },
            refreshing = refreshing,
            // The gaps are rebuilt as one set, so any row's timestamp is the set's. Taking the
            // newest keeps it honest if a partial write ever left them differing.
            refreshedAt = gaps.maxOfOrNull { it.refreshedAt },
        )
    }

    fun toResponse(
        gap: KnowledgeGap,
        owners: List<KnowledgeGapOwnerResponse>,
        firstIngested: Instant?,
    ): KnowledgeGapResponse {
        return KnowledgeGapResponse(
            id = gap.id,
            component = gap.component,
            missingTypes = gap.missingTypes.toList(),
            presentTypes = gap.presentTypes.toList(),
            lastIngested = gap.lastUpdated,
            firstIngested = firstIngested,
            refreshedAt = gap.refreshedAt,
            owners = owners,
            severity = gap.severity.apiValue,
        )
    }
}
