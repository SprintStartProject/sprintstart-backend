package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.dto.SkillDto
import java.util.UUID

// Todo: Add doc
interface SkillsApi {
    fun getSkillsByIds(skillIds: Set<UUID>): Set<SkillDto>
}
