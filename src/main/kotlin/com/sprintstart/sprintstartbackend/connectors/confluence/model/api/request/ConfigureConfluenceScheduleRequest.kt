package com.sprintstart.sprintstartbackend.connectors.confluence.model.api.request

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import jakarta.validation.Valid

/** Updates automatic synchronization settings for one project-scoped Confluence connection. */
data class ConfigureConfluenceScheduleRequest(
    @field:Valid
    val schedule: ScheduleSpec,
    val autoUpdate: Boolean,
)
