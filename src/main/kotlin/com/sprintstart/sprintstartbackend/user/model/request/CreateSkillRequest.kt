package com.sprintstart.sprintstartbackend.user.model.request

import java.util.UUID

data class CreateSkillRequest(
    val name: String,
    val roleId: UUID,
)
