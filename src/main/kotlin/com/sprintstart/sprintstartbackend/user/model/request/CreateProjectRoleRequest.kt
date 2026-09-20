package com.sprintstart.sprintstartbackend.user.model.request

import java.util.UUID

data class CreateProjectRoleRequest(
    val name: String,
    val description: String,
    val projectId: UUID? = null,
    val industry: String? = null,
)
