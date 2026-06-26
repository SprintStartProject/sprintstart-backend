package com.sprintstart.sprintstartbackend.canonical.model.dto

import java.util.UUID

data class FinishIngestionRunCommand(
    val runId: UUID,
)