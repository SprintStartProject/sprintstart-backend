package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MineStarterWorkRequest(
    @SerialName("active_source_ids") val activeSourceIds: List<String> = emptyList(),
    @SerialName("active_competency_keys") val activeCompetencyKeys: List<String> = emptyList(),
    @SerialName("last_fingerprint") val lastFingerprint: String? = null,
)

@Serializable
data class ProposedStarterTaskSchema(
    @SerialName("source_id") val sourceId: String,
    val title: String,
    val summary: String = "",
    @SerialName("competency_keys") val competencyKeys: List<String> = emptyList(),
    val rationale: String = "",
    val citations: List<CitationRefSchema> = emptyList(),
)

@Serializable
data class StarterWorkProvenanceSchema(
    @SerialName("corpus_fingerprint") val corpusFingerprint: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    val model: String? = null,
    val notes: List<String> = emptyList(),
)

@Serializable
data class StarterWorkOutcome(
    val status: String,
    val tasks: List<ProposedStarterTaskSchema> = emptyList(),
    val provenance: StarterWorkProvenanceSchema? = null,
    @SerialName("candidates_considered") val candidatesConsidered: Int = 0,
    val notes: List<String> = emptyList(),
)
