package com.sprintstart.sprintstartbackend.user.model.request.user

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import java.util.UUID

data class PatchUserRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val permissionGroup: Role? = null,
    val projectsId: Set<UUID> = emptySet(),
)
