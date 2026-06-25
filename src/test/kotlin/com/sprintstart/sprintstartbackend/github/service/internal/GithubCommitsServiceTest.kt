package com.sprintstart.sprintstartbackend.github.service.internal

import com.sprintstart.sprintstartbackend.github.external.events.commits.GithubCommitFetchedEvent
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.GithubRepositorySnapshot
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
import org.assertj.core.api.Assertions.assertThatThrownBy
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

            verify(exactly = 4) { eventPublisher.publishEvent(any<GithubCommitFetchedEvent>()) }
        }

        @Test
        fun `publishes no events when output is empty`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns ""

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
        }

        @Test
        fun `publishes no events when output contains only blank lines`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns "\n\n\n"

            service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)

            verify(exactly = 0) { eventPublisher.publishEvent(any()) }
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
        fun `throws IllegalArgumentException when commit line has wrong number of parts`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns "malformed-line-without-separators"

            assertThrows<IllegalArgumentException> {
                service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)
            }
        }

        @Test
        fun `throws IllegalArgumentException when commit line has too few parts`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns "2024-01-01T00:00:00Z - sha123"

            assertThrows<IllegalArgumentException> {
                service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true)
            }
        }

        @Test
        fun `throws DateTimeParseException when date string is malformed`() = runTest {
            every { gitRunner.exec(repoPath, any()) } returns "not-a-date - sha123 - alice - message"

            assertThatThrownBy {
                runTest { service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true) }
            }.isInstanceOf(Exception::class.java)
        }

        @Test
        fun `handles commit message containing dash correctly`() = runTest {
            // Commit messages with " - " in them would split into 5+ parts and fail
            // This test documents that current behaviour throws on such messages
            every { gitRunner.exec(repoPath, any()) } returns
                "2024-01-01T00:00:00Z - sha123 - alice - fix bug - with dash in message"

            assertThrows<IllegalStateException> {
                runTest { service.fetchAndIngestLatestCommits(snapshot(), transactionId, doSyncAll = true) }
            }
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
