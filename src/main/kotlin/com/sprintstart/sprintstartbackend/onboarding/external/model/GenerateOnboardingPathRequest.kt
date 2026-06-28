package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BlueprintStepSchema(
    val id: String,
    val title: String,
    val description: String = "",
    val requirement: String = "recommended",
    val audience: List<String> = emptyList(),
    @SerialName("min_experience")
    val minExperience: String? = null,
    val tags: List<String> = emptyList(),
    val invariant: Boolean = false,
)

@Serializable
data class BlueprintProvenanceSchema(
    @SerialName("corpus_fingerprint")
    val corpusFingerprint: String? = null,
    @SerialName("generated_at")
    val generatedAt: String? = null,
    val model: String? = null,
    val notes: List<String> = emptyList(),
)

@Serializable
data class BlueprintSchema(
    val scope: String,
    val version: String = "0",
    val source: String = "authored",
    val steps: List<BlueprintStepSchema> = emptyList(),
    val provenance: BlueprintProvenanceSchema? = null,
)

@Serializable
data class GenerateOnboardingPathRequest(
    @SerialName("working_area")
    val workingArea: String,
    val experience: String? = null,
    val skills: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val blueprints: List<BlueprintSchema> = emptyList(),
)
