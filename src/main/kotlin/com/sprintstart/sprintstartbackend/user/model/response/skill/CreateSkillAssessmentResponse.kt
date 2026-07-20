package com.sprintstart.sprintstartbackend.user.model.response.skill

import com.sprintstart.sprintstartbackend.user.external.enums.SkillLevel
import java.util.UUID

data class CreateSkillAssessmentResponse(
    val userId: UUID,
    val skillId: UUID,
    val level: SkillLevel,
)
