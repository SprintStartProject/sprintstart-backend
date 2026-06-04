package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.IngestionAiClient
import com.sprintstart.sprintstartbackend.upload.events.ArtifactUploadedEvent
import com.sprintstart.sprintstartbackend.upload.external.events.AiIngestRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths

@Component
internal class UploadEventListener(
    private val ingestionAiClient: IngestionAiClient,
    private val applicationScope: CoroutineScope,
) {
    @EventListener
    fun handleArtifactUploaded(
        event: ArtifactUploadedEvent,
    ) {
        val content = Files.readString(
            Paths.get(event.storagePath),
        )

        applicationScope.launch {
            ingestionAiClient.ingest(
                AiIngestRequest(
                    artifactId = event.artifactId.toString(),
                    filename = event.filename,
                    content = content,
                ),
            )
        }
    }
}
