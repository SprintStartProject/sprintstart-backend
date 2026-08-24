package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.BlueprintStatus
import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.RequirementType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhaseRequirement
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.CreateBlueprintPhaseRequirementRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.CreateBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phase.DeleteBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseRequirementsResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.DeleteBlueprintPhaseRequirementsResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.repository.BlueprintPhaseRepository
import com.sprintstart.sprintstartbackend.user.external.ProjectRoleApi
import com.sprintstart.sprintstartbackend.user.external.SkillsApi
import jakarta.persistence.EntityManager
import jakarta.persistence.LockModeType
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID
import kotlin.collections.orEmpty

@Service
class BlueprintPhaseRequirementService(
    private val blueprintPhaseRepository: BlueprintPhaseRepository,
    private val skillsApi: SkillsApi,
    private val projectRoleApi: ProjectRoleApi,
    private val entityManager: EntityManager,
) {
    @Transactional
    fun createBlueprintPhaseRequirementsForPhase(
        phaseId: UUID,
        request: CreateBlueprintPhaseRequirementsRequest,
    ): CreateBlueprintPhaseRequirementsResponse {
        val phase = getDraftPhase(phaseId, request.revision)

        val requestedRequirements = resolveRequirements(
            phase = phase,
            requirements = request.requirements,
        )

        val requirementsToAdd = filterNewRequirements(
            phase = phase,
            requirements = requestedRequirements,
        )

        addRequirements(phase, requirementsToAdd)

        val newRevision = phase.revision + 1

        return CreateBlueprintPhaseRequirementsResponse(
            revision = newRevision,
            requirements = phase.requirements
                .map { it.toCreateResponse() }
                .toSet(),
        )
    }

    @Transactional
    fun deleteBlueprintPhaseRequirementsForPhase(
        phaseId: UUID,
        request: DeleteBlueprintPhaseRequirementsRequest,
    ): DeleteBlueprintPhaseRequirementsResponse {
        val phase = getDraftPhase(phaseId, request.revision)

        val requirementsByType = request.requirements.groupBy { it.type }

        val skillRequirementIds = requirementsByType[RequirementType.SKILL]
            .orEmpty()
            .map { it.id }
            .toSet()

        val projectRoleRequirementIds = requirementsByType[RequirementType.PROJECT_ROLE]
            .orEmpty()
            .map { it.id }
            .toSet()

        val requirementsToRemove = mutableSetOf<UUID>()

        if (skillRequirementIds.isNotEmpty()) {
            validateAllIdsExist(
                skillRequirementIds,
                phase.requirements
                    .filter { it.type == RequirementType.SKILL }
                    .map { it.id }
                    .toSet(),
                "Parts of the selected requirements do not exist",
            )

            requirementsToRemove.addAll(skillRequirementIds)
        }

        if (projectRoleRequirementIds.isNotEmpty()) {
            validateAllIdsExist(
                projectRoleRequirementIds,
                phase.requirements
                    .filter { it.type == RequirementType.PROJECT_ROLE }
                    .map { it.id }
                    .toSet(),
                "Parts of the selected requirements do not exist",
            )

            requirementsToRemove.addAll(projectRoleRequirementIds)
        }

        var newRevision = phase.revision
        if (requirementsToRemove.isNotEmpty()) {
            entityManager.lock(
                phase,
                LockModeType.OPTIMISTIC_FORCE_INCREMENT,
            )

            phase.requirements.removeIf { it.id in requirementsToRemove }

            entityManager.flush()

            newRevision++
        }

        return DeleteBlueprintPhaseRequirementsResponse(newRevision)
    }

    // Helper methods

    private fun getDraftPhase(
        phaseId: UUID,
        expectedRevision: Long,
    ): BlueprintPhase {
        val phase = blueprintPhaseRepository
            .findById(phaseId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Phase not found with id: $phaseId",
                )
            }

        if (phase.blueprintPath.status != BlueprintStatus.DRAFT) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Blueprint can only be modified while in DRAFT status",
            )
        }

        if (phase.revision != expectedRevision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint phase has been modified by another request. Please reload and try again.",
            )
        }

        return phase
    }

    private fun resolveRequirements(
        phase: BlueprintPhase,
        requirements: Set<CreateBlueprintPhaseRequirementRequest>,
    ): Set<BlueprintPhaseRequirement> {
        val requirementsByType = requirements.groupBy { it.type }

        return buildSet {
            addAll(
                resolveSkillRequirements(
                    phase,
                    requirementsByType[RequirementType.SKILL].orEmpty(),
                ),
            )

            addAll(
                resolveProjectRoleRequirements(
                    phase,
                    requirementsByType[RequirementType.PROJECT_ROLE].orEmpty(),
                ),
            )
        }
    }

    private fun resolveSkillRequirements(
        phase: BlueprintPhase,
        requirements: List<CreateBlueprintPhaseRequirementRequest>,
    ): List<BlueprintPhaseRequirement> {
        if (requirements.isEmpty()) {
            return emptyList()
        }

        val requestedIds = requirements
            .map { it.referenceId }
            .toSet()

        val skills = skillsApi.getSkillsByIds(requestedIds)

        validateAllIdsExist(
            requestedIds = requestedIds,
            existingIds = skills.map { it.id }.toSet(),
            errorMessage = "Parts of the selected skills do not exist",
        )

        return skills.map { skill ->
            BlueprintPhaseRequirement(
                blueprintPhase = phase,
                type = RequirementType.SKILL,
                referenceId = skill.id,
                displayName = skill.name,
            )
        }
    }

    private fun resolveProjectRoleRequirements(
        phase: BlueprintPhase,
        requirements: List<CreateBlueprintPhaseRequirementRequest>,
    ): List<BlueprintPhaseRequirement> {
        if (requirements.isEmpty()) {
            return emptyList()
        }

        val requestedIds = requirements
            .map { it.referenceId }
            .toSet()

        val roles = projectRoleApi.getProjectRolesByIds(requestedIds)

        validateAllIdsExist(
            requestedIds = requestedIds,
            existingIds = roles.map { it.id }.toSet(),
            errorMessage = "Parts of the selected roles do not exist",
        )

        return roles.map { role ->
            BlueprintPhaseRequirement(
                blueprintPhase = phase,
                type = RequirementType.PROJECT_ROLE,
                referenceId = role.id,
                displayName = role.name,
            )
        }
    }

    private fun validateAllIdsExist(
        requestedIds: Set<UUID>,
        existingIds: Set<UUID>,
        errorMessage: String,
    ) {
        val missingIds = requestedIds - existingIds

        if (missingIds.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                errorMessage,
            )
        }
    }

    private fun filterNewRequirements(
        phase: BlueprintPhase,
        requirements: Set<BlueprintPhaseRequirement>,
    ): List<BlueprintPhaseRequirement> {
        val existingKeys = phase.requirements
            .map { it.type to it.referenceId }
            .toSet()

        return requirements.filter {
            (it.type to it.referenceId) !in existingKeys
        }
    }

    private fun addRequirements(
        phase: BlueprintPhase,
        requirements: List<BlueprintPhaseRequirement>,
    ) {
        if (requirements.isEmpty()) {
            return
        }

        entityManager.lock(
            phase,
            LockModeType.OPTIMISTIC_FORCE_INCREMENT,
        )

        phase.requirements.addAll(requirements)

        entityManager.flush()
    }
}
