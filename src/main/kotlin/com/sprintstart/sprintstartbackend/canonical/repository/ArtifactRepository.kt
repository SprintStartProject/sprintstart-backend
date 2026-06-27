package com.sprintstart.sprintstartbackend.canonical.repository

import com.sprintstart.sprintstartbackend.canonical.model.entity.Artifact
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ArtifactRepository : JpaRepository<Artifact, UUID> {
    fun findBySourceId(sourceId: String): Artifact?

    fun deleteBySourceId(sourceId: String)
}
