package com.sprintstart.sprintstartbackend.connectors.notion.model.api.request

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import jakarta.validation.Valid

/** Updates automatic synchronization settings for one project-scoped Notion page connection. */
data class ConfigureNotionScheduleRequest(
    @field:Valid
    val schedule: ScheduleSpec,
    val autoUpdate: Boolean,
)
