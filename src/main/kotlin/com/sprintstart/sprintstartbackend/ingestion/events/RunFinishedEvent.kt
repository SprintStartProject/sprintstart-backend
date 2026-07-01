package com.sprintstart.sprintstartbackend.ingestion.events

import java.util.UUID

data class RunFinishedEvent(
    val runId: UUID,
)
