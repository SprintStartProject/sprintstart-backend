package com.sprintstart.sprintstartbackend.onboarding.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiOnboardingEvent(
    val type: String,
    val name: String? = null,
    val detail: String? = null,
    val path: OnboardingPath? = null,
    @SerialName("path_yaml")
    val pathYaml: String? = null,
    val quality: OnboardingQuality? = null,
    val message: String? = null,
)

@Serializable
data class OnboardingPath(
    val workingArea: String? = null,
    val experience: String? = null,
    val phases: List<PathPhase> = emptyList(),
)

@Serializable
data class PathPhase(
    val title: String,
    val description: String? = null,
    val position: Int = 0,
    val steps: List<PathStep> = emptyList(),
)

@Serializable
data class Citation(
    val filename: String,
    @SerialName("chunk_id")
    val chunkId: String,
    @SerialName("section_path")
    val sectionPath: String? = null,
)

@Serializable
data class AiTask(
    val title: String,
    val description: String? = null,
)

@Serializable
data class PathStep(
    val title: String,
    val description: String? = null,
    val resources: List<PathResource> = emptyList(),
    val citations: List<Citation> = emptyList(),
    val tasks: List<AiTask> = emptyList(),
)

@Serializable
data class PathResource(
    val filename: String? = null,
    val note: String? = null,
)

@Serializable
data class OnboardingQuality(
    val score: Double? = null,
    val metrics: Map<String, String>? = null,
)
