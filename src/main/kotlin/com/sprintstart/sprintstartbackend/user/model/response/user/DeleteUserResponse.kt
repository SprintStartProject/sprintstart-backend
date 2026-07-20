package com.sprintstart.sprintstartbackend.user.model.response.user

import java.util.UUID

data class DeleteUserResponse(
    val id: UUID,
    val deleted: Boolean = true,
)
