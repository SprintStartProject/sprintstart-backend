package com.sprintstart.sprintstartbackend.user.api.dto

import com.sprintstart.sprintstartbackend.user.api.enums.Roles
import com.sprintstart.sprintstartbackend.user.api.enums.WorkingAreas

data class PatchUserRequest(
    val username: String?,
    val firstname: String?,
    val lastname: String?,
    val primaryRole: Roles?,
    val secondaryRole: Roles?,
    val workingArea: WorkingAreas?
)
