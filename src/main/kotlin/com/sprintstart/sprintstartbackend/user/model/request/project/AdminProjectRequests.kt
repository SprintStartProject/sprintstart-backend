package com.sprintstart.sprintstartbackend.user.model.request.project

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateAdminProjectRequest(
    @field:NotBlank
    val name: String,
    val description: String? = null,
    val industry: String? = null,
)

data class PatchAdminProjectRequest(
    val name: String? = null,
    val description: String? = null,
    val industry: String? = null,
)

data class AssignProjectUsersRequest(
    @field:NotEmpty
    val userIds: Set<UUID>,
)

data class SetProjectManagerRequest(
    @field:NotNull
    val managerUserId: UUID,
)

data class SetProjectIndustryRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val industry: String,
)
