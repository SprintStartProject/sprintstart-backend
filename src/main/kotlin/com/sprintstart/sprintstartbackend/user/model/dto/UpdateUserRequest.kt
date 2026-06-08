package com.sprintstart.sprintstartbackend.user.model.dto

import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import jakarta.validation.constraints.NotBlank

data class UpdateUserRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val firstname: String,
    @field:NotBlank
    val lastname: String,
    val workingArea: WorkingArea,
)
