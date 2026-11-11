package com.sprintstart.sprintstartbackend.shared.git

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class GitLogTest {
    private val gitRunner = mockk<GitOperationRunner>()
    private val onDiskOperations = OnDiskOperations()
    private val repositoryPath = Path.of("/fake/repo")

    private fun logLimitedTo(maxFilesPerCommit: Int) =
        GitLog(
            onDiskOperations,
            gitRunner,
            GitConfig(ingest = GitIngestConfig(maxFilesPerCommit = maxFilesPerCommit)),
        )

    private fun output(vararg paths: String) =
        "\u001Eabc\u001FAda\u001Fada@example.com\u001F2026-01-02T03:04:05Z\u001FFix\n" +
            paths.joinToString("") { "1\t0\t$it\u0000" } + "\u0000"

    @Test
    fun `returns the commits Git printed with their author email and files`() = runTest {
        every { gitRunner.exec(any(), any()) } returns output("src/A.kt")

        val commit = logLimitedTo(10).commits(repositoryPath, sinceRevision = null).single()

        assertThat(commit.sha).isEqualTo("abc")
        assertThat(commit.authorEmail).isEqualTo("ada@example.com")
        assertThat(commit.files).isEqualTo(CommitFiles.Listed(listOf(CommitFile("src/A.kt", 1, 0, null))))
    }

    @Test
    fun `applies the configured limit on files per commit`() = runTest {
        every { gitRunner.exec(any(), any()) } returns output("a", "b")

        val commit = logLimitedTo(1).commits(repositoryPath, sinceRevision = null).single()

        assertThat(commit.files).isEqualTo(CommitFiles.TooMany(2))
    }

    @Test
    fun `reads only the commits after a cursor when one is given`() = runTest {
        every { gitRunner.exec(any(), any()) } returns output()

        logLimitedTo(10).commits(repositoryPath, sinceRevision = "cursor123")

        verify {
            gitRunner.exec(
                repositoryPath,
                match { "cursor123..HEAD" in it.command() },
            )
        }
    }

    @Test
    fun `reads the whole history when no cursor is given`() = runTest {
        every { gitRunner.exec(any(), any()) } returns output()

        logLimitedTo(10).commits(repositoryPath, sinceRevision = null)

        verify {
            gitRunner.exec(
                repositoryPath,
                match { command -> command.command().none { it.endsWith("..HEAD") } },
            )
        }
    }

    @Test
    fun `asks Git for the changed files with renames followed and paths NUL terminated`() = runTest {
        every { gitRunner.exec(any(), any()) } returns output()

        logLimitedTo(10).commits(repositoryPath, sinceRevision = null)

        verify {
            gitRunner.exec(
                repositoryPath,
                match { command -> command.command().containsAll(listOf("--numstat", "-M", "-z")) },
            )
        }
    }
}
