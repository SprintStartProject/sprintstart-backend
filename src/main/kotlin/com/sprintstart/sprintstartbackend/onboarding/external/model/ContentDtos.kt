package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A resolved reference to an ingested document chunk, as the AI service returns it. */
@Serializable
data class CitationRefSchema(
    val filename: String,
    @SerialName("chunk_id") val chunkId: String,
    @SerialName("source_url") val sourceUrl: String? = null,
)

/** Why a generated artifact looks the way it does; carries the fingerprint that drives idempotency. */
@Serializable
data class AiProvenanceSchema(
    @SerialName("corpus_fingerprint") val corpusFingerprint: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    val model: String? = null,
    val notes: List<String> = emptyList(),
)
