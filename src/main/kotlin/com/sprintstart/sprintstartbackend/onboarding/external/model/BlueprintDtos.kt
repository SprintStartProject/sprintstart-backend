package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GenerateBlueprintsRequest(
    val scopes: List<String>? = null,
    val active: List<BlueprintSchema> = emptyList(),
)

@Serializable
data class GenerateBlueprintsResponse(
    val outcomes: List<BlueprintOutcome> = emptyList(),
)

@Serializable
data class BlueprintOutcome(
    val scope: String,
    val status: String,
    val blueprint: GeneratedBlueprint? = null,
    val notes: List<String> = emptyList(),
)

@Serializable
data class GeneratedBlueprint(
    val scope: String,
    val version: String,
    val steps: List<GeneratedBlueprintStep> = emptyList(),
    val provenance: BlueprintProvenanceSchema? = null,
)

@Serializable
data class GeneratedBlueprintStep(
    val id: String,
    val title: String,
    val description: String? = null,
    @SerialName("min_experience") val minExperience: String? = null,
    val audience: List<String> = emptyList(),
    val requirement: String = "recommended",
    val invariant: Boolean = false,
)
