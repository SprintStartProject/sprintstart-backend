package com.sprintstart.sprintstartbackend.user.model.response.skill

import com.sprintstart.sprintstartbackend.user.external.enums.SkillStatus
import java.util.UUID

data class UpdateSkillResponse(
    val id: UUID,
    val name: String,
    val roleIds: List<UUID>,
    val status: SkillStatus,
)
