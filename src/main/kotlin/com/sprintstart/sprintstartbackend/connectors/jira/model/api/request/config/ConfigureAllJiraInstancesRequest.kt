package com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.config

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import jakarta.validation.Valid

data class ConfigureAllJiraInstancesRequest(
    @Valid val schedule: ScheduleSpec,
    val autoUpdate: Boolean,
)
