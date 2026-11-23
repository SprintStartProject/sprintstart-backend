package com.sprintstart.sprintstartbackend.offboarding.model.mapper

import com.sprintstart.sprintstartbackend.offboarding.model.response.ConcentrationResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.CoupledFileResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.CouplingResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.FileRiskResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.KnowledgeResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskMapResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSettingsResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.RiskSummaryResponse
import com.sprintstart.sprintstartbackend.offboarding.model.response.SuccessorResponse
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileRisk
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskMap
import com.sprintstart.sprintstartbackend.offboarding.scoring.WeightProfile
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

private fun WeightProfile.toResponse() = RiskSettingsResponse(
    halfLifeDays = halfLifeDays,
    inactiveAfterDays = inactiveAfterDays,
    authorThreshold = authorThreshold,
    knowledgeWeight = knowledgeWeight,
    activityWeight = activityWeight,
    name = name,
    sourceWeights = sourceWeights.mapKeys { it.key.value }.toSortedMap(),
    knowledge = KnowledgeResponse(
        model = knowledge.model.name
            .lowercase()
            .replace('_', '-'),
        knowsAtLeast = knowledge.knowsAtLeast,
        foundingBonus = knowledge.foundingBonus,
    ),
)

private fun FileRisk.toResponse() = FileRiskResponse(
    repository = repository,
    path = path,
    level = level.name,
    score = rounded(score),
    departingShare = rounded(departingShare),
    sourceBreakdown = sourceBreakdown.mapKeys { it.key.value }.mapValues { rounded(it.value) }.toSortedMap(),
    recentActivity = rounded(recentActivity),
    fragmentation = rounded(fragmentation),
    reasons = reasons,
    hotspot = hotspot?.let(::rounded),
    abandonedShare = abandonedShare?.let(::rounded),
    placeholderScore = rounded(placeholderScore),
    successors = successors.map { SuccessorResponse(it.person.value, rounded(it.score)) },
    coupling = CouplingResponse(
        sum = rounded(coupling.sum),
        partners = coupling.partners.map { CoupledFileResponse(it.repository, it.path, rounded(it.degree)) },
    ),
    concentration = ConcentrationResponse(rounded(concentration.effectiveKnowers), concentration.island?.value),
    remainingKnowers = remainingKnowers.map { it.value },
    lastChangedAt = lastChangedAt,
)

private fun rounded(value: Double): Double = round(value * THOUSAND) / THOUSAND
