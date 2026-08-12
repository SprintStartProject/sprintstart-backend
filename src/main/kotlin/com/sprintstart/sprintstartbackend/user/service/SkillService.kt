package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.enums.SkillStatus
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.user.model.request.skill.CreateSkillRequest
import com.sprintstart.sprintstartbackend.user.model.request.skill.UpdateSkillRequest
import com.sprintstart.sprintstartbackend.user.model.response.skill.CreateSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.UpdateSkillResponse
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class SkillService(
    private val skillRepository: SkillRepository,
    private val projectRoleRepository: ProjectRoleRepository,
) {
    @Transactional(readOnly = true)
    @Tracked("Retrieving all skills")
    fun getAllSkills(): List<GetSkillResponse> {
        return skillRepository.findAll().map { it.toGetResponse() }
    }

    @Transactional(readOnly = true)
    @Tracked("Retrieving skill by id")
    fun getSkillById(skillId: UUID): GetSkillResponse {
        return skillRepository
            .findById(skillId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Skill with id $skillId not found") }
            .toGetResponse()
    }

    @Transactional
    @Tracked("Creating skill")
    fun createSkill(request: CreateSkillRequest): CreateSkillResponse {
        val roles = findRolesByIds(request.roleIds)
        val existingSkill = skillRepository.findByNormalizedName(request.name)

        if (existingSkill != null) {
            if (existingSkill.status != SkillStatus.RETIRED) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Skill with name '${request.name}' already exists")
            }

            existingSkill.name = request.name
            existingSkill.projectRoles = roles
            existingSkill.status = SkillStatus.ACTIVE

            return skillRepository.save(existingSkill).toCreateResponse()
        }

        return skillRepository
            .save(
                Skill(
                    name = request.name,
                    projectRoles = roles,
                ),
            ).toCreateResponse()
    }

    @Transactional
    @Tracked("Updating skill")
    fun updateSkill(skillId: UUID, request: UpdateSkillRequest): UpdateSkillResponse {
        val skill = skillRepository
            .findById(skillId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Skill with id $skillId not found") }

        request.name?.let { newName ->
            if (skillRepository.existsByNormalizedNameExcluding(newName, skillId)) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Skill with name '$newName' already exists")
            }
            skill.name = newName
        }

        request.roleIds?.let { roleIds ->
            skill.projectRoles = findRolesByIds(roleIds)
        }

        return skillRepository.save(skill).toUpdateResponse()
    }

    private fun findRolesByIds(roleIds: List<UUID>): MutableSet<ProjectRole> {
        val roles = projectRoleRepository.findAllById(roleIds)
        val foundIds = roles.map { it.id }.toSet()
        val missingIds = roleIds.toSet() - foundIds
        if (missingIds.isNotEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Project role(s) with id(s) $missingIds not found")
        }
        return roles.toMutableSet()
    }

    /**
     * Retires a skill so it can no longer be assigned to roles.
     */
    @Transactional
    @Tracked("Retiring skill")
    fun retireSkill(skillId: UUID) {
        val skill = skillRepository
            .findById(skillId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Skill with id $skillId not found") }

        skill.status = SkillStatus.RETIRED
        skillRepository.save(skill)
    }
}
