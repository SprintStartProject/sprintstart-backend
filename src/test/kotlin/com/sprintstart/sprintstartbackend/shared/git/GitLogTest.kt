package com.sprintstart.sprintstartbackend.shared.git

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.time.Instant

class GitLogTest {
    private val gitRunner = mockk<GitOperationRunner>()
    private val onDiskOperations = OnDiskOperations()
    private val log = GitLog(onDiskOperations, gitRunner)
    private val repositoryPath = Path.of("/fake/repo")

    private fun record(
        sha: String = "abc",
        author: String = "Ada",
        date: String = "2026-01-02T03:04:05Z",
        subject: String = "Fix",
    ) = listOf(sha, author, date, subject).joinToString("\u001F")

    @Test
    fun `parses a well formed record`() = runTest {
        every { gitRunner.exec(any(), any()) } returns record(sha = "abc123", author = "Ada", subject = "Fix the bug")

        val commits = log.commits(repositoryPath, sinceRevision = null)

        assertThat(commits).hasSize(1)
        assertThat(commits.first().sha).isEqualTo("abc123")
        assertThat(commits.first().authorName).isEqualTo("Ada")
        assertThat(commits.first().subject).isEqualTo("Fix the bug")
        assertThat(commits.first().committedAt).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"))
    }

    @Test
    fun `keeps a subject that contains the old printable delimiter`() = runTest {
        every { gitRunner.exec(any(), any()) } returns record(subject = "Revert \"Fix - the bug\"")

        assertThat(log.commits(repositoryPath, null).first().subject).isEqualTo("Revert \"Fix - the bug\"")
    }

    @Test
    fun `skips a stray line without losing the records around it`() = runTest {
        every { gitRunner.exec(any(), any()) } returns listOf(
            "warning: something on the merged stream",
            record(sha = "first"),
            record(sha = "second"),
        ).joinToString("\n")

        val commits = log.commits(repositoryPath, null)

        assertThat(commits.map { it.sha }).containsExactly("first", "second")
    }

    @Test
    fun `skips a record whose timestamp cannot be parsed`() = runTest {
        every { gitRunner.exec(any(), any()) } returns listOf(
            record(sha = "broken", date = "not-a-date"),
            record(sha = "fine"),
        ).joinToString("\n")

        assertThat(log.commits(repositoryPath, null).map { it.sha }).containsExactly("fine")
    }

    @Test
    fun `reads only the commits after a cursor when one is given`() = runTest {
        every { gitRunner.exec(any(), any()) } returns record()

        log.commits(repositoryPath, sinceRevision = "cursor123")

        verify {
            gitRunner.exec(
                repositoryPath,
                match { "cursor123..HEAD" in it.command() },
            )
        }
    }

    @Test
    fun `reads the whole history when no cursor is given`() = runTest {
        every { gitRunner.exec(any(), any()) } returns record()

        log.commits(repositoryPath, sinceRevision = null)

        verify {
            gitRunner.exec(
                repositoryPath,
                match { command -> command.command().none { it.endsWith("..HEAD") } },
            )
        }
    }
}
