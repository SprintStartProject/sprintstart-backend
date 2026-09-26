package com.sprintstart.sprintstartbackend.user.external.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An existing skill in the catalog passed as context to the AI skill suggestion service.
 *
 * @property id Unique skill identifier.
 * @property name Human-readable skill name.
 * @property category Optional category (e.g. "Languages & Paradigms").
 * @property universal Whether this skill applies universally across projects without artifact citations.
 */
@Serializable
data class SkillCatalogItemDto(
    val id: String,
    val name: String,
    val category: String? = null,
    val universal: Boolean = false,
)

/**
 * Request payload for the AI skill suggestion endpoint.
 *
 * @property roleName Name of the project role (e.g. "Backend Developer").
 * @property roleDescription Description of the role's responsibilities.
 * @property projectId Optional identifier of the project for RAG retrieval context.
 * @property projectIndustry Optional detected or user-specified project industry/domain.
 * @property availableSkills Current catalog of active skills available in the system.
 */
@Serializable
data class SkillSuggestionRequestDto(
    @SerialName("roleName") val roleName: String,
    @SerialName("roleDescription") val roleDescription: String = "",
    @SerialName("projectId") val projectId: String? = null,
    @SerialName("projectIndustry") val projectIndustry: String? = null,
    @SerialName("availableSkills") val availableSkills: List<SkillCatalogItemDto> = emptyList(),
)

/**
 * A single skill recommendation from the AI service.
 *
 * @property name Skill name.
 * @property category Skill category if known or suggested.
 * @property reason Why this skill is recommended for this role.
 * @property confidence Confidence level ("high", "medium", "low").
 * @property isNew Whether the skill was not found in the catalog and should be created.
 * @property chunkIds Chunk IDs providing evidence for project-specific skills.
 */
@Serializable
data class SkillSuggestionItemDto(
    val name: String,
    val category: String? = null,
    val reason: String = "",
    val confidence: String = "medium",
    @SerialName("isNew") val isNew: Boolean = false,
    @SerialName("chunkIds") val chunkIds: List<String> = emptyList(),
)

/**
 * Response payload from the AI skill suggestion endpoint.
 *
 * @property suggestions Recommended skills for the requested role.
 */
@Serializable
data class SkillSuggestionResponseDto(
    val suggestions: List<SkillSuggestionItemDto> = emptyList(),
)
