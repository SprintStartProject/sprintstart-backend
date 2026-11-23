package com.sprintstart.sprintstartbackend.offboarding.model.mapper

import com.sprintstart.sprintstartbackend.offboarding.model.response.FileRiskResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskMapResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSettingsResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSummaryResponse
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileRisk
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskMap
import com.sprintstart.sprintstartbackend.offboarding.scoring.ScoringProfile
import kotlin.math.round

private const val THOUSAND = 1000.0

/** Converts a risk map to its response, listing at most [limit] files. */
fun RiskMap.toResponse(limit: Int): RiskMapResponse {
    val listed = files.take(limit)
    return RiskMapResponse(
        asOf = asOf,
        settings = profile.toResponse(),
        departing = departing.map { it.value }.sorted(),
        unknownDeparting = unknownDeparting.map { it.value }.sorted(),
        summary = RiskSummaryResponse(
            files = summary.files,
            orphaned = summary.orphaned,
            hoarded = summary.hoarded,
            alreadyOrphaned = summary.alreadyOrphaned,
            listed = listed.size,
        ),
        files = listed.map { it.toResponse() },
    )
}

private fun ScoringProfile.toResponse() = RiskSettingsResponse(
    halfLifeDays = halfLifeDays,
    inactiveAfterDays = inactiveAfterDays,
    authorThreshold = authorThreshold,
    knowledgeWeight = knowledgeWeight,
    activityWeight = activityWeight,
)

private fun FileRisk.toResponse() = FileRiskResponse(
    repository = repository,
    path = path,
    level = level.name,
    score = rounded(score),
    departingShare = rounded(departingShare),
    recentActivity = rounded(recentActivity),
    fragmentation = rounded(fragmentation),
    remainingKnowers = remainingKnowers.map { it.value },
    lastChangedAt = lastChangedAt,
)

private fun rounded(value: Double): Double = round(value * THOUSAND) / THOUSAND
