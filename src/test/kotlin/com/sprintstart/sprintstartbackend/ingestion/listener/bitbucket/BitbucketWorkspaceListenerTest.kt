package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchingCompletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataFetchingFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.workspace.BitbucketWorkspaceMetadataMember
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.BitbucketArtifactCommand
import com.sprintstart.sprintstartbackend.ingestion.model.entity.FinishedTypes
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.BitbucketArtifactMapper
import com.sprintstart.sprintstartbackend.ingestion.service.BitbucketIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.BitbucketArtifactProviderService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class BitbucketWorkspaceListenerTest {
    private val bitbucketArtifactProviderService = mockk<BitbucketArtifactProviderService>()
    private val bitbucketArtifactMapper = mockk<BitbucketArtifactMapper>()
    private val bitbucketIngestionRunService = mockk<BitbucketIngestionRunService>()
    private val listener = BitbucketWorkspaceListener(
        bitbucketArtifactProviderService,
        bitbucketArtifactMapper,
        bitbucketIngestionRunService,
    )

    @Test
    fun `workspace metadata fetched event maps and persists artifact`() {
        val event = fetchedEvent()
        val command = mockk<BitbucketArtifactCommand>()
        every { bitbucketArtifactMapper.toCommand(event) } returns command
        every { bitbucketArtifactProviderService.persistArtifact(command) } just runs

        listener.on(event)

        verify(exactly = 1) { bitbucketArtifactProviderService.persistArtifact(command) }
    }

    @Test
    fun `workspace metadata completed event marks workspace phase finished`() {
        val runId = UUID.randomUUID()
        every { bitbucketIngestionRunService.markFetchPhaseFinished(any(), any()) } just runs

        listener.on(BitbucketWorkspaceMetadataFetchingCompletedEvent(runId))

        verify(exactly = 1) {
            bitbucketIngestionRunService.markFetchPhaseFinished(runId, FinishedTypes.ORG_METADATA)
        }
    }

    @Test
    fun `workspace metadata failed event records the failure and closes the phase`() {
        val runId = UUID.randomUUID()
        every { bitbucketIngestionRunService.markFetchPhaseFailed(any(), any(), any()) } just runs

        listener.on(BitbucketWorkspaceMetadataFetchingFailedEvent(runId, "bitbucket is down"))

        verify(exactly = 1) {
            bitbucketIngestionRunService.markFetchPhaseFailed(runId, FinishedTypes.ORG_METADATA, "bitbucket is down")
        }
        verify(exactly = 0) {
            bitbucketIngestionRunService.markFetchPhaseFinished(any(), any())
        }
    }

    private fun fetchedEvent() = BitbucketWorkspaceMetadataFetchedEvent(
        transactionId = UUID.randomUUID(),
        workspace = "sprintstart",
        uuid = "{ws-uuid}",
        name = "SprintStart",
        isPrivate = true,
        createdOn = "2024-01-01T00:00:00Z",
        url = "https://bitbucket.org/sprintstart",
        members = listOf(
            BitbucketWorkspaceMetadataMember(accountId = "{account-1}", nickname = null, displayName = "Alice"),
        ),
    )
}
