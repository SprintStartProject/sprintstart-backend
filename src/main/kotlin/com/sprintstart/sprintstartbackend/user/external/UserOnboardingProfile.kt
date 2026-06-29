package com.sprintstart.sprintstartbackend.user.external

import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import java.util.UUID

data class UserOnboardingProfile(
    val id: UUID,
    val workingArea: WorkingArea,
)
