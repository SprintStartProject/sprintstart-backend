package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toUpdateRoleSkillsResponse
import com.sprintstart.sprintstartbackend.user.model.request.CreateProjectRoleRequest
import com.sprintstart.sprintstartbackend.user.model.request.UpdateRoleSkillsRequest
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.UpdateRoleSkillsResponse
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class ProjectRoleService(
    private val projectRoleRepository: ProjectRoleRepository,
    private val userRepository: UserRepository,
    private val skillRepository: SkillRepository,
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

    @Transactional
    @Tracked("Deleting project role")
    fun deleteRole(roleId: UUID) {
        if (!projectRoleRepository.existsById(roleId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found")
        }
        projectRoleRepository.deleteById(roleId)
    }

    @Transactional
    @Tracked("Assigning project role to user")
    fun assignRoleToUser(userId: UUID, roleId: UUID) {
        val user = userRepository
            .findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id $userId not found") }
        val role = projectRoleRepository
            .findById(roleId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Project role with id $roleId not found") }

        user.projectRoles.add(role)
        userRepository.save(user)
    }

    @Transactional
    @Tracked("Unassigning project role from user")
    fun unassignRoleFromUser(userId: UUID, roleId: UUID) {
        val user = userRepository
            .findById(userId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with id $userId not found") }
        user.projectRoles.removeIf { it.id == roleId }
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
}
