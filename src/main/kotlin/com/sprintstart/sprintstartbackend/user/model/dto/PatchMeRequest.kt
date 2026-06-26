package com.sprintstart.sprintstartbackend.user.model.dto

import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea

data class PatchMeRequest(
    val workingArea: WorkingArea? = null,
    val experience: String? = null,
)
