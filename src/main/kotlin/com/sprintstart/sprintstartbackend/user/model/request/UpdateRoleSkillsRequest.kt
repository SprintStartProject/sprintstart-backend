package com.sprintstart.sprintstartbackend.user.model.request

import java.util.UUID

data class UpdateRoleSkillsRequest(
    val skillIds: List<UUID>,
)
