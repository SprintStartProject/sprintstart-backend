package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.ProjectIndustryApi
import com.sprintstart.sprintstartbackend.user.external.SkillSuggestionAiClient
import com.sprintstart.sprintstartbackend.user.external.dto.ProjectRoleShortDto
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.exceptions.SkillSuggestionAiException
import com.sprintstart.sprintstartbackend.user.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toShortDto
import com.sprintstart.sprintstartbackend.user.model.mapper.toUpdateRoleSkillsResponse
import com.sprintstart.sprintstartbackend.user.model.request.AcceptSkillSuggestionRequest
import com.sprintstart.sprintstartbackend.user.model.request.CreateProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.model.request.SuggestSkillsRequest
import com.sprintstart.sprintstartbackend.user.model.request.UpdateRoleSkillsRequest
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.SkillSuggestionItemResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.UpdateRoleSkillsResponse
import com.sprintstart.sprintstartbackend.user.model.response.user.ProjectRoleSummary
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.ProjectUserAssignmentRepository
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
@Suppress("TooManyFunctions")
class ProjectRoleService(
    private val projectRoleRepository: ProjectRoleRepository,
    private val projectUserAssignmentRepository: ProjectUserAssignmentRepository,
    private val skillRepository: SkillRepository,
    private val userRepository: UserRepository,
    private val projectIndustryApi: ProjectIndustryApi,
    private val skillSuggestionAiClient: SkillSuggestionAiClient,
) {
    private val roleSkillSuggestions =
        RoleSkillSuggestions(skillRepository, projectIndustryApi, skillSuggestionAiClient)

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
     * Requests AI-suggested skills for a project role without persisting them.
     *
     * Returns reviewable suggestions. If a suggestion matches an existing active skill,
     * it is returned with [SkillSuggestionItemResponse.skillId] set and `isNew` as false.
     * Suggestions matching retired skills are dropped.
     *
     * @param roleId The UUID of the project role.
     * @param request Optional project and industry context for RAG and industry hints.
     * @return List of reviewable skill suggestions.
     * @throws ResponseStatusException 404 when role does not exist.
     * @throws SkillSuggestionAiException when AI service fails.
     */
    @Tracked("Suggesting skills for project role")
    suspend fun suggestSkillsForRole(
        roleId: UUID,
        request: SuggestSkillsRequest? = null,
    ): List<SkillSuggestionItemResponse> {
        val role = projectRoleRepository.findById(roleId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found")
        }

        return roleSkillSuggestions.suggest(role, request)
    }

    /**
     * Accepts a skill suggestion for a project role: links an existing active skill or creates
     * a new non-universal skill and links it.
     *
     * @param roleId The UUID of the project role.
     * @param request The suggestion acceptance request containing either a skillId or a skill name.
     * @return The updated list of skills linked to the role.
     * @throws ResponseStatusException 404 when role or skill not found, 400 when invalid or retired.
     */
    @Transactional
    @Tracked("Accepting skill suggestion for project role")
    fun acceptSkillSuggestion(
        roleId: UUID,
        request: AcceptSkillSuggestionRequest,
    ): List<UpdateRoleSkillsResponse> {
        val role = projectRoleRepository.findById(roleId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found")
        }

        val skill = roleSkillSuggestions.skillToLink(request, role)
        skill.projectRoles.add(role)
        skillRepository.save(skill)

        return skillRepository.findAllByProjectRolesId(roleId).map { it.toUpdateRoleSkillsResponse() }
    }

    @Transactional
    @Tracked("Deleting project role")
    fun deleteRole(roleId: UUID) {
        if (!projectRoleRepository.existsById(roleId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found")
        }
        // Assignments are released here rather than by the database cascade: the entity mapping declares
        // none, and loaded assignments would keep a deleted role.
        val holders = projectUserAssignmentRepository.findAllHoldingRole(roleId)
        holders.forEach { it.projectRoles.removeIf { role -> role.id == roleId } }
        projectUserAssignmentRepository.saveAll(holders)
        // `sprintstart_skill_project_roles` references the role too; the skills themselves stay in the
        // catalog.
        val linkedSkills = skillRepository.findAllByProjectRolesId(roleId)
        linkedSkills.forEach { it.projectRoles.removeIf { role -> role.id == roleId } }
        skillRepository.saveAll(linkedSkills)
        projectRoleRepository.deleteById(roleId)
    }

    /**
     * The roles somebody holds on one project.
     *
     * Roles are scoped to the membership, so this is the authoritative read of what a person does
     * on a given project. 404 rather than an empty list when they are not on the project, so
     * "holds no role here" and "is not here" stay distinguishable.
     */
    @Transactional(readOnly = true)
    fun getRolesForUserOnProject(userId: UUID, projectId: UUID): List<ProjectRoleSummary> {
        val assignment = projectUserAssignmentRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User $userId is not assigned to project $projectId",
            )
        return assignment.projectRoles
            .map { ProjectRoleSummary(id = it.id, name = it.name) }
            .sortedBy { it.name }
    }

    /**
     * Gives somebody a role, checking first that they are on [projectId].
     *
     * The role is scoped to that membership: it is recorded on this assignment and applies only
     * here, so the same person can hold a different role on a different project. 404 when they are
     * not on the project. A caller with no project in hand wants the two-argument overload, which
     * applies the role across every project they belong to.
     *
     * Adding a role they already hold is a no-op, not an error — the caller's intent is already
     * satisfied.
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

        assignment.projectRoles.add(role)
        projectUserAssignmentRepository.save(assignment)
    }

    /**
     * Takes a role off somebody, checking first that they are on [projectId].
     *
     * Removes the role from this membership only; the same role on any of their other projects is
     * untouched. 404 when they are not on the project.
     */
    @Transactional
    @Tracked("Unassigning project role from user")
    fun unassignRoleFromUser(userId: UUID, projectId: UUID, roleId: UUID) {
        val assignment = projectUserAssignmentRepository.findByProjectIdAndUserId(projectId, userId)
            ?: throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "User $userId is not assigned to project $projectId",
            )
        assignment.projectRoles.removeIf { it.id == roleId }
        projectUserAssignmentRepository.save(assignment)
    }

    /**
     * Gives somebody a role on every project they belong to.
     *
     * The projectless form kept for callers (and the existing frontend) that have no project in
     * hand: roles are scoped to memberships, so "the person" is not a place a role can live, and
     * the closest faithful reading of a projectless assignment is to apply it to each of their
     * current memberships. Memberships created afterwards do not inherit it.
     */
    @Transactional
    @Tracked("Assigning project role to user")
    fun assignRoleToUser(userId: UUID, roleId: UUID) {
        val user = userRepository
            .findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id $userId not found") }
        val role = projectRoleRepository
            .findById(roleId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found") }

        user.projectAssignments.forEach { it.projectRoles.add(role) }
        userRepository.save(user)
    }

    /**
     * Takes a role off somebody on every project they belong to.
     *
     * The projectless counterpart to [assignRoleToUser]: removes the role from each of their
     * memberships, so the person no longer holds it anywhere.
     */
    @Transactional
    @Tracked("Unassigning project role from user")
    fun unassignRoleFromUser(userId: UUID, roleId: UUID) {
        val user = userRepository
            .findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id $userId not found") }
        user.projectAssignments.forEach { assignment -> assignment.projectRoles.removeIf { it.id == roleId } }
        userRepository.save(user)
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

    /**
     * Returns the project roles matching the given ids.
     *
     * Unknown ids are silently omitted, so the result may be smaller than the requested id set —
     * or empty. [ProjectRoleApiService] publishes this to the other modules.
     *
     * @param ids Ids of the project roles to resolve.
     * @return The matching project roles, mapped to [ProjectRoleShortDto]s.
     */
    fun getProjectRolesByIds(ids: Set<UUID>): Set<ProjectRoleShortDto> {
        return projectRoleRepository
            .findAllById(ids)
            .map { it.toShortDto() }
            .toSet()
    }
}
