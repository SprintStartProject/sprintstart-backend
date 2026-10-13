package com.sprintstart.sprintstartbackend.ingestion.listener.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFilesResyncedEvent
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.BitbucketArtifactFailedMapper
import com.sprintstart.sprintstartbackend.ingestion.service.BitbucketIngestionRunService
import com.sprintstart.sprintstartbackend.ingestion.service.FailedArtifactService
import com.sprintstart.sprintstartbackend.ingestion.service.provider.BitbucketArtifactProviderService
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * The fallback full ingest reports its visited paths so stored files it did not see reconcile
 * away; this locks that the event reaches the provider instead of vanishing in the listener.
 */
class BitbucketFileListenerTest {
    private val provider = mockk<BitbucketArtifactProviderService>(relaxed = true)
    private val listener = BitbucketFileListener(
        bitbucketArtifactProviderService = provider,
        bitbucketArtifactMapper = mockk(),
        bitbucketArtifactFailedMapper = mockk<BitbucketArtifactFailedMapper>(),
        bitbucketIngestionRunService = mockk<BitbucketIngestionRunService>(),
        failedArtifactService = mockk<FailedArtifactService>(),
    )

    @Test
    fun `routes a resync event to deletion reconciliation`() {
        val event = BitbucketFilesResyncedEvent(
            transactionId = UUID.randomUUID(),
            repositoryId = UUID.randomUUID(),
            workspace = "sprintstart",
            slug = "backend",
            visitedPaths = setOf("Main.kt"),
        )

        listener.on(event)

        verify { provider.reconcileDeletedFiles(event) }
    }
}
