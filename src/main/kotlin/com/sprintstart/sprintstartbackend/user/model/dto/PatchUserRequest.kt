package com.sprintstart.sprintstartbackend.user.model.dto

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea

data class PatchUserRequest(
    val username: String? = null,
    val firstname: String? = null,
    val lastname: String? = null,
    val primaryRole: Role? = null,
    val secondaryRole: Role? = null,
    val workingArea: WorkingArea? = null,
)
