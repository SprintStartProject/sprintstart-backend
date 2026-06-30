package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ArtifactRepository : JpaRepository<Artifact, UUID> {
    fun findBySourceId(sourceId: String): Artifact?

    fun deleteBySourceId(sourceId: String)

    fun findAllByIngestionRunId(runId: UUID): MutableList<Artifact>
}
