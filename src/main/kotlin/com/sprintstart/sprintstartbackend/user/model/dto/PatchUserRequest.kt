package com.sprintstart.sprintstartbackend.user.model.dto

import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea

data class PatchUserRequest(
    val workingArea: WorkingArea? = null,
)
