package com.sprintstart.sprintstartbackend.user.repository

import com.sprintstart.sprintstartbackend.user.model.entity.UserSkillAssessment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface UserSkillAssessmentRepository : JpaRepository<UserSkillAssessment, UUID> {
    fun findByUserId(userId: UUID): List<UserSkillAssessment>

    fun findBySkillId(skillId: UUID): List<UserSkillAssessment>
}
