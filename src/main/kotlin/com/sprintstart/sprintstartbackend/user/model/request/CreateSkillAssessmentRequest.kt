package com.sprintstart.sprintstartbackend.user.model.request

import com.sprintstart.sprintstartbackend.user.model.entity.SkillLevel
import java.util.UUID

data class CreateSkillAssessmentRequest(
    val skillId: UUID,
    val level: SkillLevel,
)
