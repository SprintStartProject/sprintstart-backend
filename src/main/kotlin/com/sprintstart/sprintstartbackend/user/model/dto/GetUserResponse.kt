package com.sprintstart.sprintstartbackend.user.model.dto

import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import java.util.UUID

data class GetUserResponse(
    val id: UUID,
    val authId: String,
    val username: String,
    val email: String? = null,
    val firstname: String,
    val lastname: String,
    val workingArea: WorkingArea,
)
