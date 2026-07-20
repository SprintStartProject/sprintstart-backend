package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.user.external.enums.SkillStatus
import com.sprintstart.sprintstartbackend.user.model.entity.ProjectRole
import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.model.entity.UserSkillAssessment
import com.sprintstart.sprintstartbackend.user.model.mapper.toCreateResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toGetResponse
import com.sprintstart.sprintstartbackend.user.model.mapper.toUpdateResponse
import com.sprintstart.sprintstartbackend.user.model.request.skill.CreateSkillAssessmentRequest
import com.sprintstart.sprintstartbackend.user.model.request.skill.CreateSkillRequest
import com.sprintstart.sprintstartbackend.user.model.request.skill.UpdateSkillRequest
import com.sprintstart.sprintstartbackend.user.model.response.skill.CreateSkillAssessmentResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.CreateSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillAssessmentResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.GetSkillResponse
import com.sprintstart.sprintstartbackend.user.model.response.skill.UpdateSkillResponse
import com.sprintstart.sprintstartbackend.user.repository.ProjectRoleRepository
import com.sprintstart.sprintstartbackend.user.repository.SkillRepository
import com.sprintstart.sprintstartbackend.user.repository.UserRepository
import com.sprintstart.sprintstartbackend.user.repository.UserSkillAssessmentRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class SkillService(
    private val skillRepository: SkillRepository,
    private val projectRoleRepository: ProjectRoleRepository,
    private val userRepository: UserRepository,
    private val userSkillAssessmentRepository: UserSkillAssessmentRepository,
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
     * Retires a skill so it can no longer be assigned to new users.
     * Existing assessments referencing this skill are preserved.
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

    @Transactional(readOnly = true)
    @Tracked("Retrieving user skill assessments")
    fun getUserSkillAssessments(userId: UUID): List<GetSkillAssessmentResponse> {
        if (!userRepository.existsById(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User with id $userId not found")
        }
        return userSkillAssessmentRepository.findByUserId(userId).map { it.toGetResponse() }
    }

    @Transactional
    @Tracked("Retrieving my skill assessments")
    fun getMySkillAssessments(authId: String): List<GetSkillAssessmentResponse> {
        val user = userRepository
            .findByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with authId $authId not found") }

        val assessments = userSkillAssessmentRepository
            .findByUserId(user.id)
            .map { it.toGetResponse() }

        return assessments
    }

    @Transactional
    @Tracked("Assessing skill for me")
    fun assessSkillForMe(authId: String, request: CreateSkillAssessmentRequest): CreateSkillAssessmentResponse {
        val user = userRepository
            .findByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with authId $authId not found") }

        val skill = skillRepository
            .findById(request.skillId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Skill with id ${request.skillId} not found") }

        if (skill.status == SkillStatus.RETIRED) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Skill '${skill.name}' is retired and cannot be assigned",
            )
        }

        user.skillAssessments.removeIf { it.skill.id == skill.id }

        val assessment = UserSkillAssessment(
            user = user,
            skill = skill,
            level = request.level,
        )

        user.skillAssessments.add(assessment)
        userRepository.save(user)

        val savedAssessment = user.skillAssessments.first { it.skill.id == skill.id }
        return savedAssessment.toCreateResponse()
    }
}
