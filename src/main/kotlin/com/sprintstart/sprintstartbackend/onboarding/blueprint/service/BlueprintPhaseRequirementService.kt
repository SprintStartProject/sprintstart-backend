package com.sprintstart.sprintstartbackend.onboarding.blueprint.service

import com.sprintstart.sprintstartbackend.onboarding.blueprint.external.enums.RequirementType
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.BlueprintScope
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhase
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity.BlueprintPhaseRequirement
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.CreateBlueprintPhaseRequirementRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.CreateBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.request.phaseRequirement.DeleteBlueprintPhaseRequirementsRequest
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.CreateBlueprintPhaseRequirementsResponse
import com.sprintstart.sprintstartbackend.onboarding.blueprint.model.response.phase.DeleteBlueprintPhaseRequirementsResponse
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
    private val blueprintAccessService: BlueprintAccessService,
    private val skillsApi: SkillsApi,
    private val projectRoleApi: ProjectRoleApi,
    private val entityManager: EntityManager,
) {
    @Transactional
    fun createBlueprintPhaseRequirementsForPhase(
        scope: BlueprintScope,
        phaseId: UUID,
        request: CreateBlueprintPhaseRequirementsRequest,
    ): CreateBlueprintPhaseRequirementsResponse {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)

        validateRevision(phase, request.revision)

        val resolvedRequirements = resolveRequirements(
            phase = phase,
            requirements = request.requirements,
        )

        val newRequirements = filterNewRequirements(
            phase = phase,
            requirements = resolvedRequirements,
        )

        if (newRequirements.isNotEmpty()) {
            markPhaseModified(phase)
            phase.requirements.addAll(newRequirements)
            entityManager.flush()
        }

        return CreateBlueprintPhaseRequirementsResponse(
            revision = phase.revision + if (newRequirements.isNotEmpty()) 1 else 0,
            requirements = phase.requirements
                .map { it.toCreateResponse() }
                .toSet(),
        )
    }

    @Transactional
    fun deleteBlueprintPhaseRequirementsForPhase(
        scope: BlueprintScope,
        phaseId: UUID,
        request: DeleteBlueprintPhaseRequirementsRequest,
    ): DeleteBlueprintPhaseRequirementsResponse {
        val phase = blueprintAccessService.getAuthorizedEditablePhase(scope, phaseId)

        validateRevision(phase, request.revision)

        validateAllIdsExist(
            request.requirementIds,
            phase.requirements.map { it.id }.toSet(),
            "Parts of the selected requirement do not exist",
        )

        if (request.requirementIds.isEmpty()) {
            return DeleteBlueprintPhaseRequirementsResponse(
                revision = request.revision,
            )
        }

        markPhaseModified(phase)
        phase.requirements.removeIf { it.id in request.requirementIds }
        entityManager.flush()

        return DeleteBlueprintPhaseRequirementsResponse(
            revision = request.revision + 1,
        )
    }

    // Helper methods

    private fun validateRevision(
        phase: BlueprintPhase,
        revision: Long,
    ) {
        if (phase.revision != revision) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "The blueprint phase has been modified by another request. Please reload and try again.",
            )
        }
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

    private fun markPhaseModified(phase: BlueprintPhase) {
        entityManager.lock(
            phase,
            LockModeType.OPTIMISTIC_FORCE_INCREMENT,
        )
    }
}
