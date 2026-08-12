package com.sprintstart.sprintstartbackend.onboarding.repository

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkillAssessmentSessionStatus
import com.sprintstart.sprintstartbackend.onboarding.model.entity.SkillAssessmentSession
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SkillAssessmentSessionRepository : JpaRepository<SkillAssessmentSession, UUID> {
    fun findFirstByUserIdAndProjectIdAndStatusOrderByCreatedAtDesc(
        userId: UUID,
        projectId: UUID,
        status: SkillAssessmentSessionStatus,
    ): SkillAssessmentSession?

    fun existsByUserIdAndProjectIdAndStatus(
        userId: UUID,
        projectId: UUID,
        status: SkillAssessmentSessionStatus,
    ): Boolean
}
