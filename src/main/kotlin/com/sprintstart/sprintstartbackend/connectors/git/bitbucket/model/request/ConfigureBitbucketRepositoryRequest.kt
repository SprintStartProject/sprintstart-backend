package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request

import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import jakarta.validation.Valid

internal data class ConfigureBitbucketRepositoryRequest(
    @Valid val schedule: ScheduleSpec,
    val autoUpdate: Boolean,
)
