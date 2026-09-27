package com.sprintstart.sprintstartbackend.shared.git

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path

class GitChangeSetTest {
    private val gitRunner = mockk<GitOperationRunner>()
    private val onDiskOperations = OnDiskOperations()
    private val changeSet = GitChangeSet(onDiskOperations, gitRunner)
    private val repositoryPath = Path.of("/fake/repo")

    @Test
    fun `lists changed paths without blanks`() = runTest {
        every { gitRunner.exec(any(), any()) } returns "src/A.kt\n\n src/B.kt \n"

        val paths = changeSet.changedPaths(repositoryPath, "from", "to")

        assertThat(paths).containsExactly("src/A.kt", "src/B.kt")
    }

    @Test
    fun `diffs the cursor against the new revision`() = runTest {
        every { gitRunner.exec(any(), any()) } returns ""

        changeSet.changedPaths(repositoryPath, "fromSha", "toSha")

        verify {
            gitRunner.exec(
                repositoryPath,
                match { it.command() == listOf("git", "diff", "fromSha..toSha", "--name-only") },
            )
        }
    }

    @Test
    fun `asks for names only so file contents are never diffed`() = runTest {
        every { gitRunner.exec(any(), any()) } returns ""

        changeSet.changedPaths(repositoryPath, "a", "b")

        verify { gitRunner.exec(repositoryPath, match { "--name-only" in it.command() }) }
    }
}
