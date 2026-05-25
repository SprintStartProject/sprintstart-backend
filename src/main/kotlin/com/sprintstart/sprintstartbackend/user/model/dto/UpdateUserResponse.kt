package com.sprintstart.sprintstartbackend.user.model.dto

import com.sprintstart.sprintstartbackend.user.model.Roles
import com.sprintstart.sprintstartbackend.user.model.WorkingAreas
import java.util.UUID

data class UpdateUserResponse(
    val id: UUID,
    val username: String,
    val firstname: String,
    val lastname: String,
    val primaryRole: Roles,
    val secondaryRole: Roles,
    val workingArea: WorkingAreas
)
