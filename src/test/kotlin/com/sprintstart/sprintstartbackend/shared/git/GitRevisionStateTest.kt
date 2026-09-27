package com.sprintstart.sprintstartbackend.shared.git

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class GitRevisionStateTest {
    private val gitRunner = mockk<GitOperationRunner>()
    private val onDiskOperations = OnDiskOperations()
    private val state = GitRevisionState(onDiskOperations, gitRunner)
    private val repositoryPath = Path.of("/fake/repo")

    @Test
    fun `trims the newline git writes after a revision`() = runTest {
        every { gitRunner.exec(any(), any()) } returns "abc123\n"

        assertThat(state.currentRevision(repositoryPath)).isEqualTo("abc123")
    }

    @Test
    fun `reports up to date when local and remote revisions match`() = runTest {
        every { gitRunner.exec(any(), match { it.command().contains("rev-parse") }) } returns "same\n"
        every { gitRunner.exec(any(), match { it.command().contains("ls-remote") }) } returns "same\tHEAD\n"

        assertThat(state.isUpToDate(repositoryPath)).isTrue()
    }

    @Test
    fun `reports out of date when the remote moved on`() = runTest {
        every { gitRunner.exec(any(), match { it.command().contains("rev-parse") }) } returns "local\n"
        every { gitRunner.exec(any(), match { it.command().contains("ls-remote") }) } returns "remote\tHEAD\n"

        assertThat(state.isUpToDate(repositoryPath)).isFalse()
    }

    @Test
    fun `advances the clone by fetching and then resetting rather than merging`() = runTest {
        every { gitRunner.exec(any(), match { it.command().contains("rev-parse") }) } returns "new\n"
        every { gitRunner.exec(any(), match { it.command().contains("fetch") }) } returns ""
        every { gitRunner.exec(any(), match { it.command().contains("reset") }) } returns ""

        val revision = state.updateLocal(repositoryPath)

        assertThat(revision).isEqualTo("new")
        verify { gitRunner.exec(repositoryPath, match { it.command() == listOf("git", "fetch", "origin") }) }
        verify {
            gitRunner.exec(
                repositoryPath,
                match { it.command() == listOf("git", "reset", "--hard", "FETCH_HEAD") },
            )
        }
        verify(exactly = 0) {
            gitRunner.exec(repositoryPath, match { it.command().contains("merge") })
        }
    }
}
