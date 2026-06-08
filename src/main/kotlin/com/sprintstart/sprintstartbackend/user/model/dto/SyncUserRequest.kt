package com.sprintstart.sprintstartbackend.user.model.dto

import jakarta.validation.constraints.NotBlank

data class SyncUserRequest(
    @field:NotBlank
    val authId: String,
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val firstname: String,
    @field:NotBlank
    val lastname: String,
)
