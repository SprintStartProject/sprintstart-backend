package com.sprintstart.sprintstartbackend.user.external.events

import com.sprintstart.sprintstartbackend.user.external.enums.WorkingArea
import java.util.UUID

data class UserWorkingAreaUpdatedEvent(
    val userId: UUID,
    val oldWorkingArea: WorkingArea,
    val newWorkingArea: WorkingArea,
)
