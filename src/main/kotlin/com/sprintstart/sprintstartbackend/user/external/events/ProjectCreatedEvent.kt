package com.sprintstart.sprintstartbackend.user.external.events

import java.util.UUID

data class ProjectCreatedEvent(
    val projectId: UUID,
)
