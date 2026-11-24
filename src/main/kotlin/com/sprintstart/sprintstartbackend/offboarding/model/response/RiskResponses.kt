package com.sprintstart.sprintstartbackend.offboarding.model.response

import java.time.Instant

/** The risk map of a simulated departure. */
data class RiskMapResponse(
    val asOf: Instant,
    val settings: RiskSettingsResponse,
    val departing: List<String>,
    val unknownDeparting: List<String>,
    val summary: RiskSummaryResponse,
    val files: List<FileRiskResponse>,
)

/** The settings a ranking was computed with. */
data class RiskSettingsResponse(
    val halfLifeDays: Double,
    val inactiveAfterDays: Long,
    val authorThreshold: Double,
    val knowledgeWeight: Double,
    val activityWeight: Double,
    val name: String,
    val sourceWeights: Map<String, Double>,
    val knowledge: KnowledgeResponse = KnowledgeResponse(),
    val ranking: String = "departing-share",
)

/** How a ranking decided who knows a file; maps stored before there was a choice used authorship. */
data class KnowledgeResponse(
    val model: String = "authorship",
    val knowsAtLeast: Double = 1.0,
    val foundingBonus: Double = 3.0,
)

/** How many files fall in each class; `listed` is how many of the at-risk files `files` carries. */
data class RiskSummaryResponse(
    val files: Int,
    val orphaned: Int,
    val hoarded: Int,
    val alreadyOrphaned: Int,
    val listed: Int,
)

/**
 * How unevenly the knowledge of a file is spread.
 *
 * @property effectiveKnowers How many equally strong knowers the spread is worth.
 * @property island The person who holds almost all of the knowledge, if one does.
 */
data class ConcentrationResponse(
    val effectiveKnowers: Double,
    val island: String?,
)

/**
 * The files that change together with a file.
 *
 * @property sum The coupling of every partner added up.
 * @property partners The most strongly coupled files, strongest first.
 */
data class CouplingResponse(
    val sum: Double,
    val partners: List<CoupledFileResponse>,
)

/** A file that changes together with another, and how strongly, from 0 to 1. */
data class CoupledFileResponse(
    val repository: String,
    val path: String,
    val degree: Double,
)

/** A person who could take a file over, with the score of how well they know its surroundings. */
data class SuccessorResponse(
    val person: String,
    val score: Double,
)

/** One file at risk, with the measures that put it there. */
data class FileRiskResponse(
    val repository: String,
    val path: String,
    val level: String,
    val score: Double,
    val departingShare: Double,
    val sourceBreakdown: Map<String, Double>,
    val recentActivity: Double,
    val fragmentation: Double,
    val concentration: ConcentrationResponse? = null,
    val coupling: CouplingResponse? = null,
    val hotspot: Double? = null,
    val abandonedShare: Double? = null,
    val placeholderScore: Double? = null,
    val successors: List<SuccessorResponse> = emptyList(),
    val reasons: List<String> = emptyList(),
    val remainingKnowers: List<String>,
    val lastChangedAt: Instant,
)
