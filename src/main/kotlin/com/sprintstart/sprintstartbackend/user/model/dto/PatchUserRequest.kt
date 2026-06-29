package com.sprintstart.sprintstartbackend.user.model.dto

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea

data class PatchUserRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val workingArea: WorkingArea? = null,
    val permissionGroup: Role? = null,
)
