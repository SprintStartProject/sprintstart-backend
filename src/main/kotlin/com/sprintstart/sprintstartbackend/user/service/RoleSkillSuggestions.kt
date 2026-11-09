package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryApi
import com.sprintstart.sprintstartbackend.user.external.SkillSuggestionAiClient
import com.sprintstart.sprintstartbackend.user.external.enums.SkillStatus
import com.sprintstart.sprintstartbackend.user.external.model.SkillCatalogItemDto
import com.sprintstart.sprintstartbackend.user.external.model.SkillSuggestionItemDto
import com.sprintstart.sprintstartbackend.user.external.model.SkillSuggestionRequestDto
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.model.request.AcceptSkillSuggestionRequest
import com.sprintstart.sprintstartbackend.user.model.request.SuggestSkillsRequest
import com.sprintstart.sprintstartbackend.user.model.response.skill.SkillSuggestionItemResponse
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

/** Turns the AI's skill suggestions for a project role into reviewable ones, and accepted ones into skills. */
internal class RoleSkillSuggestions(
    private val skillRepository: SkillRepository,
    private val projectIndustryApi: ProjectIndustryApi,
    private val skillSuggestionAiClient: SkillSuggestionAiClient,
) {
    /**
     * Asks the AI for skills [role] needs, without persisting anything.
     *
     * A suggestion matching an existing active skill comes back linked to it; one matching a retired
     * skill is dropped; the rest are offered as new.
     */
    suspend fun suggest(role: ProjectRole, request: SuggestSkillsRequest?): List<SkillSuggestionItemResponse> {
        val industry = resolveIndustry(request?.projectId, request?.industry)
        val activeSkills = skillRepository.findAll().filter { it.status == SkillStatus.ACTIVE }

        val aiRequest = SkillSuggestionRequestDto(
            roleName = role.name,
            roleDescription = role.description,
            projectId = request?.projectId?.toString(),
            projectIndustry = industry,
            availableSkills = activeSkills.map {
                SkillCatalogItemDto(
                    id = it.id.toString(),
                    name = it.name,
                    category = it.category,
                    universal = it.universal,
                )
            },
        )
        val catalog = activeSkills.associateBy { it.name.trim().lowercase() }

        return skillSuggestionAiClient
            .suggestSkills(aiRequest)
            .suggestions
            .mapNotNull { reviewable(it, catalog) }
    }

    /** The skill an accepted suggestion stands for: the one it names, an existing one by name, or a new one. */
    fun skillToLink(request: AcceptSkillSuggestionRequest, role: ProjectRole): Skill {
        if (request.skillId != null) {
            return existingSkill(request.skillId)
        }
        return skillNamed(request.name, request.category, role)
    }

    private fun reviewable(
        suggestion: SkillSuggestionItemDto,
        catalog: Map<String, Skill>,
    ): SkillSuggestionItemResponse? {
        val trimmedName = suggestion.name.trim()
        if (trimmedName.isBlank()) return null

        val catalogMatch = catalog[trimmedName.lowercase()]
        if (catalogMatch != null) {
            return SkillSuggestionItemResponse(
                skillId = catalogMatch.id,
                name = catalogMatch.name,
                category = catalogMatch.category ?: suggestion.category,
                reason = suggestion.reason,
                confidence = suggestion.confidence,
                isNew = false,
                chunkIds = suggestion.chunkIds,
            )
        }
        if (skillRepository.findByNormalizedName(trimmedName)?.status == SkillStatus.RETIRED) return null

        return SkillSuggestionItemResponse(
            skillId = null,
            name = trimmedName,
            category = suggestion.category,
            reason = suggestion.reason,
            confidence = suggestion.confidence,
            isNew = true,
            chunkIds = suggestion.chunkIds,
        )
    }

    private fun existingSkill(skill: UUID): Skill {
        val found = skillRepository.findById(skill).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Skill with id $skill not found")
        }
        if (found.status == SkillStatus.RETIRED) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Skill '${found.name}' is retired and cannot be assigned",
            )
        }
        return found
    }

    private fun skillNamed(name: String?, category: String?, role: ProjectRole): Skill {
        val trimmedName = name?.trim()
        if (trimmedName.isNullOrBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill name or skillId must be provided")
        }
        val existing = skillRepository.findByNormalizedName(trimmedName)
        if (existing != null) {
            if (existing.status == SkillStatus.RETIRED) {
                throw ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Skill '$trimmedName' is retired and cannot be assigned",
                )
            }
            return existing
        }

        return Skill(
            name = trimmedName,
            category = category,
            universal = false,
            projectRoles = mutableSetOf(role),
        )
    }

    private suspend fun resolveIndustry(project: UUID?, fallbackIndustry: String?): String? {
        if (project != null) {
            val evaluated = projectIndustryApi.getOrEvaluateIndustry(project)
            if (!evaluated.isNullOrBlank()) {
                return evaluated
            }
        }
        return fallbackIndustry?.takeIf { it.isNotBlank() }
    }
}
