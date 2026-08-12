package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GenerateCompetencyGraphRequest(
    @SerialName("active_competencies") val activeCompetencies: List<ActiveCompetencySchema> = emptyList(),
    /**
     * The areas already in use, so the generator groups into them instead of inventing a synonym.
     *
     * The area is free text — a fixed taxonomy cannot fit a codebase nobody has seen — so this is
     * what stops the vocabulary fragmenting into "auth" / "Authentication" / "Auth & Identity". The
     * backend normalises on write as well; sending them is what makes the model *choose* an existing
     * area rather than have one corrected out from under it.
     *
     * Same mechanic as `active_competencies`: show the model what exists rather than let it guess.
     */
    @SerialName("existing_areas") val existingAreas: List<String> = emptyList(),
    /**
     * Competencies somebody deliberately removed, so the generator does not bring them back.
     *
     * Sent as key **and** label: dedup matches on the key *and* on embedding similarity, and the
     * thing a tombstone has to stop is a *rephrasing* — which the key check alone would miss. A
     * tombstone the generator never sees is not a tombstone.
     */
    @SerialName("tombstoned_competencies")
    val tombstonedCompetencies: List<TombstonedCompetencySchema> = emptyList(),
    @SerialName("last_fingerprint") val lastFingerprint: String? = null,
)

@Serializable
data class ActiveCompetencySchema(
    val key: String,
    val label: String,
    val description: String = "",
    val kind: String,
    /** What it is about, so the generator can see how the existing vocabulary is grouped. */
    val area: String? = null,
    @SerialName("repo_ref") val repoRef: String? = null,
)

/** A competency that was removed on purpose, and must not be proposed again. */
@Serializable
data class TombstonedCompetencySchema(
    val key: String,
    val label: String,
)

@Serializable
data class GraphProposalOutcome(
    val status: String,
    val competencies: List<ProposedCompetencySchema> = emptyList(),
    val provenance: GraphProvenanceSchema? = null,
    @SerialName("chunks_retrieved") val chunksRetrieved: Int = 0,
    val notes: List<String> = emptyList(),
)

@Serializable
data class ProposedCompetencySchema(
    val key: String,
    val label: String,
    val description: String = "",
    val kind: String,
    /** What it is about, for grouping. Null when the generator could not place it in one. */
    val area: String? = null,
    @SerialName("repo_ref") val repoRef: String? = null,
)

@Serializable
data class GraphProvenanceSchema(
    @SerialName("corpus_fingerprint") val corpusFingerprint: String? = null,
    @SerialName("generated_at") val generatedAt: String? = null,
    val model: String? = null,
    val notes: List<String> = emptyList(),
)
