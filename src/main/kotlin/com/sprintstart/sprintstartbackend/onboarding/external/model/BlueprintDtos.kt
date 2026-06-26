package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.Serializable

@Serializable
data class GenerateBlueprintsRequest(
    val scopes: List<String>? = null,
)

@Serializable
data class GenerateBlueprintsResponse(
    val outcomes: List<BlueprintOutcome> = emptyList(),
)

@Serializable
data class BlueprintOutcome(
    val scope: String,
    val status: String,
    val message: String? = null,
)

@Serializable
data class DraftListResponse(
    val items: List<DraftSummary> = emptyList(),
)

@Serializable
data class DraftSummary(
    val scope: String,
    val createdAt: String? = null,
    val summary: String? = null,
)

@Serializable
data class BlueprintDiff(
    val scope: String,
    val changes: List<DiffChange> = emptyList(),
    val blocked: Boolean = false,
)

@Serializable
data class DiffChange(
    val action: String,
    val stepId: String? = null,
    val description: String? = null,
)

@Serializable
data class Blueprint(
    val scope: String,
    val version: String,
    val steps: List<BlueprintStep> = emptyList(),
)

@Serializable
data class BlueprintStep(
    val id: String? = null,
    val title: String,
    val description: String? = null,
)

@Serializable
data class VersionListResponse(
    val scope: String,
    val versions: List<String> = emptyList(),
)

@Serializable
data class RollbackBlueprintRequest(
    val version: String,
)
