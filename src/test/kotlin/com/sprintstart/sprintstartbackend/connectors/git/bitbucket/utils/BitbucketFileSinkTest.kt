package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileDeletedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchFailedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.files.BitbucketFileFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.connectors.git.utils.GitFileChange
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.util.UUID

class BitbucketFileSinkTest {
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val connection = BitbucketConnection(
        workspace = "sprintstart",
        slug = "sprintstart-backend",
        credentialAuthId = "auth-id",
        credentialName = "team-token",
    )
    private val transactionId = UUID.randomUUID()
    private val sink = BitbucketFileSink(eventPublisher, connection, transactionId, BitbucketSourceUrls())

    @Test
    fun `maps a modified file onto a fetched event`() = runTest {
        sink.onBatch(listOf(GitFileChange.Modified("src/Main.kt", "rev-1", "content", "hash")))

        verify {
            eventPublisher.publishEvent(
                match<BitbucketFileFetchedEvent> {
                    it.transactionId == transactionId &&
                        it.repositoryId == connection.id &&
                        it.workspace == "sprintstart" &&
                        it.slug == "sprintstart-backend" &&
                        it.path == "src/Main.kt" &&
                        it.content == "content" &&
                        it.sourceUrl.contains("src/Main.kt")
                },
            )
        }
    }

    @Test
    fun `maps a deleted file onto a deletion event`() = runTest {
        sink.onBatch(listOf(GitFileChange.Deleted("gone.kt", "rev-1")))

        verify {
            eventPublisher.publishEvent(
                match<BitbucketFileDeletedEvent> {
                    it.transactionId == transactionId &&
                        it.repositoryId == connection.id &&
                        it.path == "gone.kt"
                },
            )
        }
    }

    @Test
    fun `maps a failure onto a fetch-failed event`() = runTest {
        sink.onFailure("big.json", "above the ingest size limit")

        verify {
            eventPublisher.publishEvent(
                match<BitbucketFileFetchFailedEvent> {
                    it.transactionId == transactionId &&
                        it.path == "big.json" &&
                        it.reason.contains("size limit")
                },
            )
        }
    }
}
