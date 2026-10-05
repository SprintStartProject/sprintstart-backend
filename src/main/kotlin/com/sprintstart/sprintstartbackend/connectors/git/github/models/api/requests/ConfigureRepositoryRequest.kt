package com.sprintstart.sprintstartbackend.connectors.git.github.models.api.requests

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import jakarta.validation.Valid

data class ConfigureRepositoryRequest(
    @Valid val schedule: ScheduleSpec,
    val autoUpdate: Boolean,
)
