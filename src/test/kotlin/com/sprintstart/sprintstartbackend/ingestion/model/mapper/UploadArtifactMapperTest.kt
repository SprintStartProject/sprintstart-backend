package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.ingestion.external.model.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.upload.external.api.UploadedArtifactReader
import com.sprintstart.sprintstartbackend.upload.external.events.ingestion.ArtifactUploadedEvent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

class UploadArtifactMapperTest {
    private val uploadedArtifactReader = mockk<UploadedArtifactReader>()
    private val mapper = UploadArtifactMapper(uploadedArtifactReader)

    @Test
    fun `toCommand maps uploaded artifact id as source id and keeps storage path in metadata`() {
        val runId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val uploaderId = UUID.randomUUID()
        val event = ArtifactUploadedEvent(
            transactionId = runId,
            projectId = projectId,
            artifactId = artifactId,
            filename = "notes.md",
            storagePath = "/uploads/$artifactId/notes.md",
            mime = "text/markdown",
            hash = "hash",
            uploadedAt = Instant.parse("2026-07-11T10:15:30Z"),
            uploaderId = uploaderId,
        )
        every { uploadedArtifactReader.readText(artifactId) } returns "# Notes"

        val result = mapper.toCommand(event)

        assertThat(result.ingestionRunId).isEqualTo(runId)
        assertThat(result.projectId).isEqualTo(projectId)
        assertThat(result.sourceSystem).isEqualTo(SourceSystem.UPLOAD)
        assertThat(result.sourceId).isEqualTo(artifactId.toString())
        assertThat(result.artifactType).isEqualTo(ArtifactType.FILE)
        assertThat(result.title).isEqualTo("notes.md")
        assertThat(result.content).isEqualTo("# Notes")
        assertThat(result.mime).isEqualTo("text/markdown")
        assertThat(result.language).isEqualTo("Markdown")
        assertThat(result.hash).isEqualTo("hash")
        assertThat(result.metadata).isEqualTo(
            UploadArtifactMetadata(
                storagePath = "/uploads/$artifactId/notes.md",
                actorId = uploaderId,
            ),
        )
    }

    @Test
    fun `toCommand reads pdf bytes as base64 content`() {
        val runId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val uploaderId = UUID.randomUUID()
        val pdfBytes = "%PDF-1.7".toByteArray()
        val event = ArtifactUploadedEvent(
            transactionId = runId,
            projectId = projectId,
            artifactId = artifactId,
            filename = "guide.pdf",
            storagePath = "/uploads/$artifactId/guide.pdf",
            mime = "application/pdf",
            hash = "hash",
            uploadedAt = Instant.parse("2026-07-11T10:15:30Z"),
            uploaderId = uploaderId,
        )
        every { uploadedArtifactReader.readBytes(artifactId) } returns pdfBytes

        val result = mapper.toCommand(event)

        assertThat(result.content).isEqualTo(Base64.getEncoder().encodeToString(pdfBytes))
        assertThat(result.language).isNull()
        assertThat(result.mime).isEqualTo("application/pdf")
        verify(exactly = 1) {
            uploadedArtifactReader.readBytes(artifactId)
        }
        verify(exactly = 0) {
            uploadedArtifactReader.readText(artifactId)
        }
    }

    @Test
    fun `toCommand reads image bytes as base64 content`() {
        val runId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val uploaderId = UUID.randomUUID()
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val event = ArtifactUploadedEvent(
            transactionId = runId,
            projectId = projectId,
            artifactId = artifactId,
            filename = "diagram.png",
            storagePath = "/uploads/$artifactId/diagram.png",
            mime = "image/png",
            hash = "hash",
            uploadedAt = Instant.parse("2026-07-11T10:15:30Z"),
            uploaderId = uploaderId,
        )
        every { uploadedArtifactReader.readBytes(artifactId) } returns imageBytes

        val result = mapper.toCommand(event)

        assertThat(result.content).isEqualTo(Base64.getEncoder().encodeToString(imageBytes))
        assertThat(result.language).isNull()
        assertThat(result.mime).isEqualTo("image/png")
        verify(exactly = 1) {
            uploadedArtifactReader.readBytes(artifactId)
        }
        verify(exactly = 0) {
            uploadedArtifactReader.readText(artifactId)
        }
    }

    @Test
    fun `toCommand keeps unsupported binary content null`() {
        val runId = UUID.randomUUID()
        val projectId = UUID.randomUUID()
        val artifactId = UUID.randomUUID()
        val uploaderId = UUID.randomUUID()
        val event = ArtifactUploadedEvent(
            transactionId = runId,
            projectId = projectId,
            artifactId = artifactId,
            filename = "archive.zip",
            storagePath = "/uploads/$artifactId/archive.zip",
            mime = "application/zip",
            hash = "hash",
            uploadedAt = Instant.parse("2026-07-11T10:15:30Z"),
            uploaderId = uploaderId,
        )

        val result = mapper.toCommand(event)

        assertThat(result.content).isNull()
        assertThat(result.language).isNull()
        assertThat(result.mime).isEqualTo("application/zip")
        verify(exactly = 0) {
            uploadedArtifactReader.readBytes(artifactId)
        }
        verify(exactly = 0) {
            uploadedArtifactReader.readText(artifactId)
        }
    }
}
