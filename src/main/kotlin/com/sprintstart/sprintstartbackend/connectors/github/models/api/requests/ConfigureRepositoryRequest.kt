package com.sprintstart.sprintstartbackend.connectors.github.models.api.requests

import com.sprintstart.sprintstartbackend.connectors.github.models.ScheduleSpec
import jakarta.validation.Valid

data class ConfigureRepositoryRequest(
    @Valid val schedule: ScheduleSpec,
    val autoUpdate: Boolean,
)
