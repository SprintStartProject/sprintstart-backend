package com.sprintstart.sprintstartbackend.user.model.dto

import com.sprintstart.sprintstartbackend.user.model.entity.SkillLevel
import java.util.UUID

data class SkillAssessmentDto(
    val userId: UUID,
    val skillId: UUID,
    val level: SkillLevel,
)
