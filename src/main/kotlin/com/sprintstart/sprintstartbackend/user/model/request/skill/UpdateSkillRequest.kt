package com.sprintstart.sprintstartbackend.user.model.request.skill

import java.util.UUID

data class UpdateSkillRequest(
    val name: String?,
    val roleIds: List<UUID>?,
)
