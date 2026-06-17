package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.events.ArtifactUploadedEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths

@Component
internal class UploadEventListener(
    private val artifactIngestionService: ArtifactIngestionService,
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
            artifactIngestionService.ingestUploadedArtifact(event, content)
        }
    }
}
