package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
import jakarta.persistence.EntityManager
import java.util.UUID

/** Stores the rows a changed-file test needs: a finished run and commit artifacts of it. */
internal class CommitArtifacts(
    private val entityManager: EntityManager,
) {
    private val run = IngestionRun(
        id = UUID.randomUUID(),
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.COMPLETED,
    ).also(entityManager::persist)

    /** Stores a GitHub commit artifact with the given source id. */
    fun commit(sourceId: String): Artifact {
        val artifact = Artifact(
            sourceSystem = SourceSystem.GITHUB,
            sourceId = sourceId,
            sourceUrl = "https://github.com/acme/backend",
            artifactType = ArtifactType.COMMIT,
            title = sourceId,
            content = "content",
            mime = null,
            language = null,
            createdAtSource = null,
            updatedAtSource = null,
            ingestionRun = run,
            hash = null,
        )
        entityManager.persist(artifact)
        return artifact
    }
}
