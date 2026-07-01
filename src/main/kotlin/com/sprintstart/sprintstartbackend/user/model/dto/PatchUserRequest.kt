package com.sprintstart.sprintstartbackend.user.model.dto

import com.sprintstart.sprintstartbackend.user.external.enums.Role

data class PatchUserRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val permissionGroup: Role? = null,
)
