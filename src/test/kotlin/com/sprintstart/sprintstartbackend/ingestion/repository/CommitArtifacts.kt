package com.sprintstart.sprintstartbackend.ingestion.repository

import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.shared.model.SourceSystem
import jakarta.persistence.EntityManager
import java.util.UUID

/** Stores the rows an evidence test needs: a finished run and commit or pull request artifacts of it. */
internal class CommitArtifacts(
    private val entityManager: EntityManager,
) {
    private val run = IngestionRun(
        id = UUID.randomUUID(),
        sourceSystem = SourceSystem.GITHUB,
        status = IngestionRunStatus.COMPLETED,
    ).also(entityManager::persist)

    /** Stores a GitHub commit artifact with the given source id. */
    fun commit(sourceId: String): Artifact = store(sourceId, ArtifactType.COMMIT)

    /** Stores a GitHub pull request artifact with the given source id. */
    fun pullRequest(sourceId: String): Artifact = store(sourceId, ArtifactType.PULL_REQUEST)

    private fun store(sourceId: String, type: ArtifactType): Artifact {
        val artifact = Artifact(
            sourceSystem = SourceSystem.GITHUB,
            sourceId = sourceId,
            sourceUrl = "https://github.com/acme/backend",
            artifactType = type,
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
