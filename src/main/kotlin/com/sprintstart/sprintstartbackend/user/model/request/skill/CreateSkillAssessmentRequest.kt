package com.sprintstart.sprintstartbackend.user.model.request.skill

import com.sprintstart.sprintstartbackend.user.external.enums.SkillLevel
import java.util.UUID

data class CreateSkillAssessmentRequest(
    val skillId: UUID,
    val level: SkillLevel,
)
