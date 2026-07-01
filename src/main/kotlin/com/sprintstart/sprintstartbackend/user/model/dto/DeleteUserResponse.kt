package com.sprintstart.sprintstartbackend.user.model.dto

import java.util.UUID

data class DeleteUserResponse(
    val id: UUID,
    val deleted: Boolean = true,
)
