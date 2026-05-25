package com.sprintstart.sprintstartbackend.user.api.dto

import com.sprintstart.sprintstartbackend.user.api.enums.WorkingAreas

data class CreateUserRequest(
    val username: String,
    val firstname: String,
    val lastname: String,
    val workingArea: WorkingAreas
)
