package com.sprintstart.sprintstartbackend.canonical.model.mapper

import com.sprintstart.sprintstartbackend.canonical.model.entity.ArtifactType

object SourceIdFactory {
    fun buildSourceId(
        repositoryOwner: String,
        repositoryName: String,
        type: ArtifactType,
        unique: String?,
    ): String =
        "github:$repositoryOwner/$repositoryName:$type:$unique"
}
