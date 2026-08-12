package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingTrackApi
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toUpdateRoleSkillsResponse
import com.sprintstart.sprintstartbackend.user.model.request.CreateProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.model.request.UpdateRoleSkillsRequest
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.UpdateRoleSkillsResponse
import com.sprintstart.sprintstartbackend.user.model.response.user.ProjectRoleSummary
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ProjectRoleService(
    private val projectRoleRepository: ProjectRoleRepository,
    private val projectUserAssignmentRepository: ProjectUserAssignmentRepository,
    private val skillRepository: SkillRepository,
    private val onboardingTrackApi: OnboardingTrackApi,
) {
    @Transactional(readOnly = true)
    @Tracked("Retrieving all project roles")
    fun getAllRoles(): List<ProjectRole> {
        return projectRoleRepository.findAll()
    }

    @Transactional
    @Tracked("Creating new project role")
    fun createRole(request: CreateProjectRoleRequest): ProjectRole {
        val role = ProjectRole(
            name = request.name,
            description = request.description,
        )
        return projectRoleRepository.save(role)
    }

    /**
     * Points a role at an onboarding track, or clears it.
     *
     * The key is validated against the live tracks rather than stored as free text: an unknown key
     * silently resolves to the default track everywhere it is read, so a typo would look exactly
     * like a working configuration and would only surface as a hire reading the wrong words.
     *
     * A null or blank key clears the track. That is not a failure state -- it resolves to the
     * default, which is what every role did before tracks existed.
     *
     * @throws ResponseStatusException 404 if the role does not exist, 400 if the key is not a
     * known track.
     */
    @Transactional
    fun setOnboardingTrack(roleId: UUID, trackKey: String?): ProjectRole {
        val role = projectRoleRepository
            .findById(roleId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found") }

        val normalized = trackKey?.trim()?.takeIf { it.isNotBlank() }
        if (normalized != null && normalized !in onboardingTrackApi.trackKeys()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown onboarding track '$normalized'")
        }

        role.onboardingTrackKey = normalized
        return projectRoleRepository.save(role)
    }

    @Transactional
    @Tracked("Deleting project role")
    fun deleteRole(roleId: UUID) {
        if (!projectRoleRepository.existsById(roleId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found")
        }
        // Let every assignment go of the role first. The database would cascade (V4), but the entity
        // mapping declares no cascade — so tests, which build schema from entities, would fail on the
        // constraint — and a DB-side cascade leaves loaded assignments holding a role that no longer
        // exists. Doing it here makes it the same everywhere.
        val holders = projectUserAssignmentRepository.findAllHoldingRole(roleId)
        holders.forEach { it.user.projectRoles.removeIf { role -> role.id == roleId } }
        projectUserAssignmentRepository.saveAll(holders)
        projectRoleRepository.deleteById(roleId)
    }

    /**
     * The roles somebody holds **on one project**.
     *
     * Needed because the per-person surfaces show the union across their projects, which cannot be
     * edited: taking a role off has to say *where*. 404 rather than an empty list when they are not
     * on the project, so "holds no role here" and "is not here" stay distinguishable.
     */
    @Transactional(readOnly = true)
    fun getRolesForUserOnProject(userId: UUID, projectId: UUID): List<ProjectRoleSummary> {
        val assignment = projectUserAssignmentRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User $userId is not assigned to project $projectId",
            )
        return assignment.user.projectRoles
            .map { ProjectRoleSummary(id = it.id, name = it.name) }
            .sortedBy { it.name }
    }

    /**
     * Gives somebody a role **on one project**.
     *
     * Roles are held per project, so this needs an assignment to hang off: 404 when the person is
     * not on that project, rather than silently creating membership as a side effect of setting a
     * role. Adding a role they already hold here is a no-op, not an error — the caller's intent is
     * already satisfied.
     */
    @Transactional
    @Tracked("Assigning project role to user")
    fun assignRoleToUser(userId: UUID, projectId: UUID, roleId: UUID) {
        val assignment = projectUserAssignmentRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User $userId is not assigned to project $projectId",
            )
        val role = projectRoleRepository
            .findById(roleId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found") }

        assignment.user.projectRoles.add(role)
        projectUserAssignmentRepository.save(assignment)
    }

    /** Takes a role off somebody **on one project**, leaving whatever they hold elsewhere alone. */
    @Transactional
    @Tracked("Unassigning project role from user")
    fun unassignRoleFromUser(userId: UUID, projectId: UUID, roleId: UUID) {
        val assignment = projectUserAssignmentRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User $userId is not assigned to project $projectId",
            )
        assignment.user.projectRoles.removeIf { it.id == roleId }
        projectUserAssignmentRepository.save(assignment)
    }

    @Transactional(readOnly = true)
    @Tracked("Retrieving skills for project role")
    fun getSkillsForRole(roleId: UUID): List<GetSkillResponse> {
        if (!projectRoleRepository.existsById(roleId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found")
        }
        return skillRepository.findAllByProjectRolesId(roleId).map { it.toGetResponse() }
    }

    /**
     * Replaces the full set of skills linked to a project role.
     *
     * Skills being unassigned from the role are rejected if doing so would leave them linked to
     * no role at all, since every skill must belong to at least one project role.
     */
    @Transactional
    @Tracked("Updating skills for project role")
    fun setSkillsForRole(roleId: UUID, request: UpdateRoleSkillsRequest): List<UpdateRoleSkillsResponse> {
        val role = projectRoleRepository
            .findById(roleId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found") }

        val newSkillIds = request.skillIds.toSet()
        val skillsToAssign = skillRepository.findAllById(request.skillIds)
        val missingIds = newSkillIds - skillsToAssign.map { it.id }.toSet()
        if (missingIds.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Skill(s) with id(s) $missingIds not found")
        }

        val currentSkills = skillRepository.findAllByProjectRolesId(roleId)
        val skillsToUnassign = currentSkills.filter { it.id !in newSkillIds }
        val orphanedSkills = skillsToUnassign.filter { it.projectRoles.size == 1 }
        if (orphanedSkills.isNotEmpty()) {
            val names = orphanedSkills.joinToString(", ") { it.name }
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Cannot unassign role from skill(s) that would be left with no roles: $names",
            )
        }

        skillsToUnassign.forEach { it.projectRoles.remove(role) }
        skillsToAssign.forEach { it.projectRoles.add(role) }
        skillRepository.saveAll(skillsToUnassign + skillsToAssign)

        return skillRepository.findAllByProjectRolesId(roleId).map { it.toUpdateRoleSkillsResponse() }
    }
}
