package com.sprintstart.sprintstartbackend.user.model.dto

data class PatchMeRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val profileIcon: String? = null,
)
