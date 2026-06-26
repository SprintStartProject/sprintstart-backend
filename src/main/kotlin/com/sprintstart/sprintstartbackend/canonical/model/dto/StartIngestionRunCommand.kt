package com.sprintstart.sprintstartbackend.canonical.model.dto

import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import java.util.UUID

data class StartIngestionRunCommand(
    val runId: UUID = UUID.randomUUID(),
    val sourceSystem: SourceSystem,
    val expectedSourceIds: List<String> = emptyList(),
)