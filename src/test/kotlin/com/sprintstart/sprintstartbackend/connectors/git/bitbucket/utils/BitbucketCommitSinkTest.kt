package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.utils

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.external.events.commits.BitbucketCommitFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketConnection
import com.sprintstart.sprintstartbackend.shared.git.GitCommit
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant
import java.util.UUID

class BitbucketCommitSinkTest {
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val connection = BitbucketConnection(
        workspace = "sprintstart",
        slug = "sprintstart-backend",
        credentialAuthId = "auth-id",
        credentialName = "team-token",
    )
    private val transactionId = UUID.randomUUID()
    private val sink = BitbucketCommitSink(eventPublisher, connection, transactionId, BitbucketSourceUrls())

    @Test
    fun `maps a commit onto a fetched event with a bitbucket source url`() = runTest {
        val committedAt = Instant.parse("2026-03-04T05:06:07Z")

        sink.onCommit(GitCommit("sha-1", "Ada", committedAt, "Fix the bug"))

        verify {
            eventPublisher.publishEvent(
                match<BitbucketCommitFetchedEvent> {
                    it.transactionId == transactionId &&
                        it.repositoryId == connection.id &&
                        it.workspace == "sprintstart" &&
                        it.slug == "sprintstart-backend" &&
                        it.sha == "sha-1" &&
                        it.author == "Ada" &&
                        it.committedAt == committedAt &&
                        it.subject == "Fix the bug" &&
                        it.sourceUrl == "https://bitbucket.org/sprintstart/sprintstart-backend/commits/sha-1"
                },
            )
        }
    }
}
