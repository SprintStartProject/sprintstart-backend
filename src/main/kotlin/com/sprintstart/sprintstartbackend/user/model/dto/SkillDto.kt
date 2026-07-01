package com.sprintstart.sprintstartbackend.user.model.dto

import java.util.UUID

data class SkillDto(
    val id: UUID,
    val name: String,
    val roleId: UUID,
)
