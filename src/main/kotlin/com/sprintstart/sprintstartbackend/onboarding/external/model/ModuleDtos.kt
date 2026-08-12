package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProposeModuleRequest(
    @SerialName("competency_key") val competencyKey: String,
    @SerialName("competency_label") val competencyLabel: String,
    @SerialName("competency_description") val competencyDescription: String = "",
    val level: String = "beginner",
    @SerialName("last_fingerprint") val lastFingerprint: String? = null,
)

/**
 * One proposed page of a module. `kind` mirrors
 * [com.sprintstart.sprintstartbackend.onboarding.external.enums.ModulePageKind]; an unrecognized
 * value is dropped on persist rather than trusted, the same rule the AI applies on its side.
 */
@Serializable
data class ProposedModulePageSchema(
    val kind: String,
    val title: String,
    val body: String = "",
    val citations: List<CitationRefSchema> = emptyList(),
)

/**
 * The module's gating check, proposed alongside its pages because the check belongs to the module
 * rather than to a per-user step.
 */
@Serializable
data class ProposedModuleVerificationSchema(
    val type: String = "KNOWLEDGE",
    val prompt: String,
    val rubric: String? = null,
)

@Serializable
data class ProposedModuleSchema(
    @SerialName("competency_key") val competencyKey: String,
    val level: String,
    val title: String,
    val summary: String = "",
    val pages: List<ProposedModulePageSchema> = emptyList(),
    val verification: ProposedModuleVerificationSchema? = null,
)

@Serializable
data class ModuleProposalOutcome(
    val status: String,
    val module: ProposedModuleSchema? = null,
    val provenance: AiProvenanceSchema? = null,
    @SerialName("chunks_retrieved") val chunksRetrieved: Int = 0,
    @SerialName("pages_dropped") val pagesDropped: Int = 0,
    val notes: List<String> = emptyList(),
)
