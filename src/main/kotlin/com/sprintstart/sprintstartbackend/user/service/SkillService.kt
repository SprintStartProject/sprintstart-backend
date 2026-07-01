package com.sprintstart.sprintstartbackend.user.service

import com.sprintstart.sprintstartbackend.user.model.dto.SkillAssessmentDto
import com.sprintstart.sprintstartbackend.user.model.dto.SkillDto
import com.sprintstart.sprintstartbackend.user.model.entity.Skill
import com.sprintstart.sprintstartbackend.user.model.entity.UserSkillAssessment
import com.sprintstart.sprintstartbackend.user.model.request.CreateSkillAssessmentRequest
import com.sprintstart.sprintstartbackend.user.model.request.CreateSkillRequest
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
    fun getAllSkills(): List<SkillDto> {
        return skillRepository.findAll().map { skill ->
            SkillDto(
                id = skill.id,
                name = skill.name,
                roleId = skill.projectRole.id,
            )
        }
    }

    @Transactional
    fun createSkill(request: CreateSkillRequest): SkillDto {
        val role = projectRoleRepository
            .findById(request.roleId)
            .orElseThrow {
                ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Project role with id ${request.roleId} not found",
                )
            }

        val skill = Skill(
            name = request.name,
            projectRole = role,
        )
        val savedSkill = skillRepository.save(skill)

        return SkillDto(
            id = savedSkill.id,
            name = savedSkill.name,
            roleId = savedSkill.projectRole.id,
        )
    }

    @Transactional
    fun deleteSkill(skillId: UUID) {
        if (!skillRepository.existsById(skillId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Skill with id $skillId not found")
        }
        skillRepository.deleteById(skillId)
    }

    @Transactional(readOnly = true)
    fun getUserSkillAssessments(userId: UUID): List<SkillAssessmentDto> {
        if (!userRepository.existsById(userId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "User with id $userId not found")
        }
        return userSkillAssessmentRepository.findByUserId(userId).map { assessment ->
            SkillAssessmentDto(
                userId = assessment.user.id,
                skillId = assessment.skill.id,
                level = assessment.level,
            )
        }
    }

    @Transactional
    fun assessSkillForMe(authId: String, request: CreateSkillAssessmentRequest): SkillAssessmentDto {
        val user = userRepository
            .findByAuthId(authId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "User with authId $authId not found") }

        val skill = skillRepository
            .findById(request.skillId)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Skill with id ${request.skillId} not found") }

        // Remove existing assessment for this skill
        user.skillAssessments.removeIf { it.skill.id == skill.id }

        val assessment = UserSkillAssessment(
            user = user,
            skill = skill,
            level = request.level,
        )

        user.skillAssessments.add(assessment)
        userRepository.save(user)

        // After saving user, return the assessment DTO
        val savedAssessment = user.skillAssessments.first { it.skill.id == skill.id }
        return SkillAssessmentDto(
            userId = savedAssessment.user.id,
            skillId = savedAssessment.skill.id,
            level = savedAssessment.level,
        )
    }
}
