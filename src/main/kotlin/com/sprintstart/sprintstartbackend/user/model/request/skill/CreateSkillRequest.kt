package com.sprintstart.sprintstartbackend.user.model.request.skill

import java.util.UUID

data class CreateSkillRequest(
    val name: String,
    val roleIds: List<UUID>,
)
