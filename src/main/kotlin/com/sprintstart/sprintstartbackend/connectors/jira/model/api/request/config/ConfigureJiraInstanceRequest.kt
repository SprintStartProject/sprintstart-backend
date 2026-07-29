package com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.config

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

data class ConfigureJiraInstanceRequest(
    @NotBlank
    val instanceUrl: String,
    @Valid val schedule: ScheduleSpec,
    val autoUpdate: Boolean,
)
