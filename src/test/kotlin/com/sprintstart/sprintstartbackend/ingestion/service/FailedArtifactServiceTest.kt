package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.UploadArtifactMetadata
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ArtifactFailedCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRun
import com.sprintstart.sprintstartbackend.ingestion.model.entity.IngestionRunStatus
import com.sprintstart.sprintstartbackend.ingestion.model.entity.SourceSystem
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.IngestionRunNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.IngestionRunRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID

class FailedArtifactServiceTest {
    private val ingestionRunRepository = mockk<IngestionRunRepository>()
    private val service = FailedArtifactService(ingestionRunRepository)

    @Test
    fun `addFailedArtifact appends failed item and increments failed count`() {
        val run = ingestionRun()
        every { ingestionRunRepository.findById(run.id) } returns Optional.of(run)

        service.addFailedArtifact(command(run.id))

        assertThat(run.failedCount).isEqualTo(1)
        val failedItem = run.failedItems.single()
        assertThat(failedItem.sourceId).isEqualTo("source-id")
        assertThat(failedItem.sourceUrl).isNull()
        assertThat(failedItem.artifactType).isEqualTo(ArtifactType.FILE)
        assertThat(failedItem.reason).isEqualTo("Upload failed")
    }

    @Test
    fun `addFailedArtifact throws when run is missing`() {
        val runId = UUID.randomUUID()
        every { ingestionRunRepository.findById(runId) } returns Optional.empty()

        assertThatThrownBy { service.addFailedArtifact(command(runId)) }
            .isInstanceOf(IngestionRunNotFoundException::class.java)
            .hasMessageContaining(runId.toString())
    }

    private fun command(runId: UUID) = ArtifactFailedCommand(
        transactionId = runId,
        sourceId = "source-id",
        sourceUrl = null,
        artifactType = ArtifactType.FILE,
        reason = "Upload failed",
        metadata = UploadArtifactMetadata(
            storagePath = null,
            actorId = UUID.randomUUID(),
        ),
    )

    private fun ingestionRun() = IngestionRun(
        id = UUID.randomUUID(),
        sourceSystem = SourceSystem.UPLOAD,
        status = IngestionRunStatus.RUNNING,
    )
}
