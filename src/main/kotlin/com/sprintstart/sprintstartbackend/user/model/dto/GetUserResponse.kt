package com.sprintstart.sprintstartbackend.user.model.dto

import com.sprintstart.sprintstartbackend.user.external.enums.Role
import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import java.util.UUID

data class GetUserResponse(
    val id: UUID,
    val authId: String,
    val username: String,
    val email: String? = null,
    val firstName: String,
    val lastName: String,
    val workingArea: WorkingArea,
    val permissionGroup: Role,
    val enabled: Boolean,
    val profileIcon: String? = null,
    val hasCompletedOnboarding: Boolean,
)
