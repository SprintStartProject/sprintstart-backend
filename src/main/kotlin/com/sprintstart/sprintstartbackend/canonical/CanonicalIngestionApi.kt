package com.sprintstart.sprintstartbackend.canonical

import com.sprintstart.sprintstartbackend.canonical.model.dto.FailIngestionArtifactCommand
import com.sprintstart.sprintstartbackend.canonical.model.dto.FinishIngestionRunCommand
import com.sprintstart.sprintstartbackend.canonical.model.dto.IngestCanonicalArtifactCommand
import com.sprintstart.sprintstartbackend.canonical.model.dto.StartIngestionRunCommand
import com.sprintstart.sprintstartbackend.canonical.model.dto.UpdateIngestionStepCommand
import java.util.UUID

interface CanonicalIngestionApi {
    fun startRun (command : StartIngestionRunCommand): UUID
    fun updateStep (command : UpdateIngestionStepCommand)
    fun ingestArtifact(command : IngestCanonicalArtifactCommand) : ArtifactIngestionResponse
    fun failArtifact(command : FailIngestionArtifactCommand)
    fun finishRun(command : FinishIngestionRunCommand)
}