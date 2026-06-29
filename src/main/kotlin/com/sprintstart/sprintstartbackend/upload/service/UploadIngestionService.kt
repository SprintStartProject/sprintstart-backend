package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.IngestionAiClient
import com.sprintstart.sprintstartbackend.upload.events.ArtifactUploadedEvent
import com.sprintstart.sprintstartbackend.upload.external.UploadIngestionApi
import com.sprintstart.sprintstartbackend.upload.external.events.AiIngestRequest
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Sends artifact content to the ingestion API, regardless of where the content originated.
 *
 * Uploads and GitHub sync both ultimately need to call the same downstream ingestion endpoint.
 * This service keeps that request-building logic in one place so event listeners remain thin and
 * cross-module integrations stay explicit.
 */
@Service
class UploadIngestionService(
    private val ingestionAiClient: IngestionAiClient,
) : UploadIngestionApi {
    suspend fun ingestUploadedArtifact(event: ArtifactUploadedEvent, content: String) {
        ingest(
            artifactId = event.artifactId.toString(),
            filename = event.filename,
            content = content,
        )
    }

    override suspend fun ingestGithubFile(path: String, content: String, sourceUrl: String) {
        ingest(
            artifactId = githubArtifactId(sourceUrl),
            filename = githubFilename(path),
            content = content,
        )
    }

    private suspend fun ingest(artifactId: String, filename: String, content: String) {
        ingestionAiClient.ingest(
            AiIngestRequest(
                artifactId = artifactId,
                filename = filename,
                content = content,
            ),
        )
    }

    private fun githubArtifactId(sourceUrl: String): String =
        UUID.nameUUIDFromBytes(sourceUrl.toByteArray(StandardCharsets.UTF_8)).toString()

    private fun githubFilename(path: String): String =
        path.substringAfterLast('/').substringAfterLast('\\')
}
