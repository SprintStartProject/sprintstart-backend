package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.AiWebClientImpl
import com.sprintstart.sprintstartbackend.upload.events.ArtifactUploadedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.AiIngestRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths

@Component
internal class UploadEventListener(
    private val aiWebClient: AiWebClientImpl,
    @Value("\${sprintstart.ai.base-url}")
    private val aiBaseUrl: String,
) {
    @EventListener
    fun handleArtifactUploaded(
        event: ArtifactUploadedEvent,
    ) {
        val content = Files.readString(
            Paths.get(event.storagePath),
        )

        aiWebClient.ingest(
            URI("$aiBaseUrl/api/v1/ingest"),
            AiIngestRequest(
                artifactId = event.artifactId.toString(),
                filename = event.filename,
                content = content,
            ),
        )
    }
}
