package com.sprintstart.sprintstartbackend.user.model.request.project

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import java.util.UUID

data class CreateAdminProjectRequest(
    @field:NotBlank
    val name: String,
    val description: String? = null,
)

data class PatchAdminProjectRequest(
    val name: String? = null,
    val description: String? = null,
)

data class AssignProjectUsersRequest(
    @field:NotEmpty
    val userIds: Set<UUID>,
)
