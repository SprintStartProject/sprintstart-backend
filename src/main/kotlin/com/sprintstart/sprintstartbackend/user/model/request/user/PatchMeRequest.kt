package com.sprintstart.sprintstartbackend.user.model.request.user

import java.util.UUID

data class PatchMeRequest(
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val profileIcon: String? = null,
    val projectsId: Set<UUID>,
)
