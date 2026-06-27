package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchFailedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitsFetchCompletedEvent
import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitsFetchStartedEvent
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.GithubRepositorySnapshot
import com.sprintstart.sprintstartbackend.github.models.exceptions.GithubCommitsFetchFailedPartiallyException
import com.sprintstart.sprintstartbackend.github.util.CustomOnDiskCache
import com.sprintstart.sprintstartbackend.github.util.GitOperationRunner
import com.sprintstart.sprintstartbackend.github.util.OnDiskOperations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.context.ApplicationEventPublisher
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

class GithubCommitsServiceTest {
    private val onDiskOperations = OnDiskOperations()
    private val customCache = mockk<CustomOnDiskCache>()
    private val eventPublisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val gitRunner = mockk<GitOperationRunner>()

    private lateinit var service: GithubCommitsService

    private val transactionId = UUID.randomUUID()
    private val repoPath = Path.of("/fake/repo")
    private val repo = GithubRepositoryConnection(owner = "owner", name = "repo")

    @BeforeEach
    fun setUp() {
        service = GithubCommitsService(
            onDiskOperations = onDiskOperations,
            customCache = customCache,
            eventPublisher = eventPublisher,
            gitRunner = gitRunner,
        )
        coEvery { customCache.getLocalRepositoryPath("owner", "repo") } returns repoPath
    }

    // ── git command routing ───────────────────────────────────────────────────

    @Nested
    inner class GitCommandRouting {
        @Test
        fun `uses gitCommits when doSyncAll is true`() = runTest {
            every {
                gitRunner.exec(
                    repoPath,
                    match {
                        it.command() ==
                            listOf("git", "log", "--pretty=format:%cI - %H - %an - %s")
                    },
                )
            } returns ""

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            verify {
                gitRunner.exec(
                    repoPath,
                    match { pb ->
                        pb.command() == listOf("git", "log", "--pretty=format:%cI - %H - %an - %s")
                    },
                )
            }
        }

        @Test
        fun `uses gitCommitsAfter when doSyncAll is false`() = runTest {
            val syncAt = Instant.parse("2024-01-01T00:00:00Z")
            every {
                gitRunner.exec(repoPath, match { it.command().any { arg -> arg.contains("--after") } })
            } returns ""

            service.fetchAndIngestLatestCommits(snapshot(lastCommitsSyncAt = syncAt), transactionId, doSyncAll = false)

            verify {
                gitRunner.exec(
                    repoPath,
                    match { pb ->
                        pb.command().any { it.contains("--after=") }
                    },
                )
            }
        }

        @Test
        fun `passes lastCommitsSyncAt timestamp to gitCommitsAfter`() = runTest {
            val syncAt = Instant.parse("2024-06-15T10:00:00Z")
            every {
                gitRunner.exec(repoPath, match { it.command().any { arg -> arg.contains("--after") } })
            } returns ""

            service.fetchAndIngestLatestCommits(snapshot(lastCommitsSyncAt = syncAt), transactionId)

            verify {
                gitRunner.exec(
                    repoPath,
                    match { pb ->
                        pb.command().any { it.contains(syncAt.toString()) }
                    },
                )
            }
        }
    }

    // ── event publishing ──────────────────────────────────────────────────────

    @Nested
    inner class EventPublishing {
        @Test
        fun `publishes one event per commit line plus summary`() = runTest {
            every {
                gitRunner.exec(repoPath, any())
            } returns
                """
                2024-01-01T00:00:00Z - abc123 - alice - fix bug
                2024-01-02T00:00:00Z - def456 - bob - add feature
                2024-01-03T00:00:00Z - ghi789 - carol - refactor
                """.trimIndent()

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            verify(exactly = 5) { eventPublisher.publishEvent(any<GithubCommitFetchedEvent>()) }
        }

        @Test
        fun `publishes lifecycle events when output is empty`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns ""

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            val events = mutableListOf<Any>()
            verify(exactly = 2) { eventPublisher.publishEvent(capture(events)) }
            assertThat(events).anyMatch { it is GithubCommitsFetchStartedEvent }
            assertThat(events).anyMatch { it is GithubCommitsFetchCompletedEvent }
        }

        @Test
        fun `publishes lifecycle events when output contains only blank lines`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns "\n\n\n"

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            val events = mutableListOf<Any>()
            verify(exactly = 2) { eventPublisher.publishEvent(capture(events)) }
            assertThat(events).anyMatch { it is GithubCommitsFetchStartedEvent }
            assertThat(events).anyMatch { it is GithubCommitsFetchCompletedEvent }
        }

        @Test
        fun `publishes GithubCommitsFetchingStartedEvent on start`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns ""

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            verify { eventPublisher.publishEvent(any<GithubCommitsFetchStartedEvent>()) }
        }

        @Test
        fun `publishes GithubCommitsFetchingCompletedEvent on completion`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns ""

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            verify { eventPublisher.publishEvent(any<GithubCommitsFetchCompletedEvent>()) }
        }

        @Test
        fun `publishes GithubCommitFetchFailedEvent on parse failure`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns "malformed"

            assertThrows<GithubCommitsFetchFailedPartiallyException> {
                service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)
            }

            verify { eventPublisher.publishEvent(any<GithubCommitFetchFailedEvent>()) }
        }

        @Test
        fun `collects partial failure and throws`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns
                """
                2024-01-01T00:00:00Z - sha1 - alice - good commit
                bad-line-without-separators
                2024-01-03T00:00:00Z - sha3 - carol - another good commit
                """.trimIndent()

            assertThrows<GithubCommitsFetchFailedPartiallyException> {
                service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)
            }

            val events = mutableListOf<Any>()
            verify(exactly = 4) { eventPublisher.publishEvent(capture(events)) }
            val fetchedEvents = events.filterIsInstance<GithubCommitFetchedEvent>()
            assertThat(fetchedEvents).hasSize(2)
        }
    }

    // ── event field mapping ───────────────────────────────────────────────────

    @Nested
    inner class EventFieldMapping {
        @Test
        fun `maps commit fields to event correctly`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns
                "2024-01-15T10:30:00Z - abc123def456 - alice - fix authentication bug"

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            val eventSlot = slot<GithubCommitFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            with(eventSlot.captured) {
                assertThat(sha).isEqualTo("abc123def456")
                assertThat(author).isEqualTo("alice")
                assertThat(msg).isEqualTo("fix authentication bug")
                assertThat(date).isEqualTo(Instant.parse("2024-01-15T10:30:00Z"))
                assertThat(this.transactionId).isEqualTo(transactionId)
            }
        }

        @Test
        fun `passes same transactionId to all commit events`() = runTest {
            every {
                gitRunner.exec(repoPath, any())
            } returns
                """
                2024-01-01T00:00:00Z - sha1 - alice - commit one
                2024-01-02T00:00:00Z - sha2 - bob - commit two
                """.trimIndent()

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            val capturedEvents = mutableListOf<GithubCommitFetchedEvent>()
            verify(exactly = 2) { eventPublisher.publishEvent(capture(capturedEvents)) }

            assertThat(capturedEvents).allMatch { it.transactionId == transactionId }
        }
    }

    // ── parseCommit ───────────────────────────────────────────────────────────

    @Nested
    inner class ParseCommit {
        @Test
        fun `throws GithubCommitsFetchFailedPartiallyException when commit line has wrong number of parts`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns "malformed-line-without-separators"

            assertThrows<GithubCommitsFetchFailedPartiallyException> {
                service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)
            }
        }

        @Test
        fun `throws GithubCommitsFetchFailedPartiallyException when commit line has too few parts`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns "2024-01-01T00:00:00Z - sha123"

            assertThrows<GithubCommitsFetchFailedPartiallyException> {
                service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)
            }
        }

        @Test
        fun `throws GithubCommitsFetchFailedPartiallyException when date string is malformed`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns "not-a-date - sha123 - alice - message"

            assertThrows<GithubCommitsFetchFailedPartiallyException> {
                service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)
            }
        }

        @Test
        fun `handles commit message containing dash correctly`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns
                "2024-01-01T00:00:00Z - sha123 - alice - fix bug - with dash in message"

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            val eventSlot = slot<GithubCommitFetchedEvent>()
            verify { eventPublisher.publishEvent(capture(eventSlot)) }

            assertThat(eventSlot.captured.msg).isEqualTo("fix bug - with dash in message")
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun snapshot(
        lastCommitsSyncAt: Instant = Instant.parse("2024-01-01T00:00:00Z"),
    ) = GithubRepositorySnapshot(
        repository = repo,
        lastCommitsSyncAt = lastCommitsSyncAt,
    )
}
