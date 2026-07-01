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

/**
 * A user's assessed skill with a proficiency [level], mirroring the AI service's
 * `SkillAssessmentSchema` and the frontend `UserSkillAssessment`. Carrying the
 * level (instead of a bare tag) lets proficiency drive AI personalization.
 *
 * [level] is one of `beginner`, `intermediate`, `advanced`, `expert`
 * (case-insensitive; unknown values are handled gracefully by the AI service).
 */
@Serializable
data class SkillAssessmentSchema(
    val name: String,
    val level: String = "beginner",
)

@Serializable
data class GenerateOnboardingPathRequest(
    @SerialName("working_area")
    val workingArea: String,
    val skills: List<SkillAssessmentSchema> = emptyList(),
    val tags: List<String> = emptyList(),
    val blueprints: List<BlueprintSchema> = emptyList(),
)
