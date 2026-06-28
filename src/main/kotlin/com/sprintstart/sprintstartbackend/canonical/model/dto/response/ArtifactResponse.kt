package com.sprintstart.sprintstartbackend.canonical.model.dto.response

import com.sprintstart.sprintstartbackend.canonical.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.canonical.model.entity.SourceSystem
import java.util.UUID

data class ArtifactResponse(
    val id: UUID = UUID.randomUUID(),
    var title: String?,
    val sourceSystem: SourceSystem,
    val sourceUrl: String?,
    val repositoryFullName: String,
    val artifactType: ArtifactType,
)
