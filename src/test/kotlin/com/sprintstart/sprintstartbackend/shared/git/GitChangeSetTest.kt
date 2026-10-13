package com.sprintstart.sprintstartbackend.shared.git

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GitChangeSetTest {
    private val gitRunner = mockk<GitOperationRunner>()
    private val onDiskOperations = OnDiskOperations()
    private val changeSet = GitChangeSet(onDiskOperations, gitRunner)
    private val repositoryPath = Path.of("/fake/repo")

    @TempDir
    lateinit var tempRepo: Path

    @Test
    fun `lists changed paths split on NUL without trimming`() = runTest {
        every { gitRunner.exec(any(), any()) } returns "src/A.kt\u0000src/B.kt\u0000"

        val paths = changeSet.changedPaths(repositoryPath, "from", "to")

        assertThat(paths).containsExactly("src/A.kt", "src/B.kt")
    }

    @Test
    fun `ignores empty entries produced by the trailing terminator`() = runTest {
        every { gitRunner.exec(any(), any()) } returns "a.kt\u0000\u0000"

        assertThat(changeSet.changedPaths(repositoryPath, "from", "to")).containsExactly("a.kt")
    }

    @Test
    fun `keeps surrounding whitespace because it is part of the path`() = runTest {
        every { gitRunner.exec(any(), any()) } returns " src/B.kt \u0000"

        assertThat(changeSet.changedPaths(repositoryPath, "from", "to")).containsExactly(" src/B.kt ")
    }

    @Test
    fun `keeps quoted-looking and special names verbatim`() = runTest {
        val umlaut = "Grüße.md"
        val tabbed = "dir/with\ttab.kt"
        val newline = "dir/with\nnewline.kt"
        every { gitRunner.exec(any(), any()) } returns "$umlaut\u0000$tabbed\u0000$newline\u0000"

        assertThat(changeSet.changedPaths(repositoryPath, "from", "to"))
            .containsExactly(umlaut, tabbed, newline)
    }

    @Test
    fun `diffs the cursor against the new revision with NUL termination`() = runTest {
        every { gitRunner.exec(any(), any()) } returns ""

        changeSet.changedPaths(repositoryPath, "fromSha", "toSha")

        verify {
            gitRunner.exec(
                repositoryPath,
                match { it.command() == listOf("git", "diff", "-z", "--name-only", "fromSha..toSha") },
            )
        }
    }

    @Test
    fun `asks for names only so file contents are never diffed`() = runTest {
        every { gitRunner.exec(any(), any()) } returns ""

        changeSet.changedPaths(repositoryPath, "a", "b")

        verify { gitRunner.exec(repositoryPath, match { "--name-only" in it.command() && "-z" in it.command() }) }
    }

    @Test
    fun `reports a renamed umlaut file with its real name against real git`() = runTest {
        val realChangeSet = GitChangeSet(OnDiskOperations(), DefaultGitOperationRunner())
        initRepo(tempRepo)
        writeAndCommit(tempRepo, "Grüße.md", "old", "first")
        val from = revParse(tempRepo)
        writeAndCommit(tempRepo, "Grüße.md", "new", "second")
        val to = revParse(tempRepo)

        val paths = realChangeSet.changedPaths(tempRepo, from, to)

        assertThat(paths).containsExactly("Grüße.md")
    }

    @Test
    fun `reports tab and newline names unquoted against real git`() = runTest {
        val realChangeSet = GitChangeSet(OnDiskOperations(), DefaultGitOperationRunner())
        initRepo(tempRepo)
        writeAndCommit(tempRepo, "plain.txt", "base", "first")
        val from = revParse(tempRepo)
        writeFile(tempRepo, "with\ttab.txt", "tabbed")
        writeFile(tempRepo, "with\nnewline.txt", "newline")
        git(tempRepo, "add", "-A")
        git(tempRepo, "commit", "-m", "second", "--quiet")
        val to = revParse(tempRepo)

        val paths = realChangeSet.changedPaths(tempRepo, from, to)

        assertThat(paths).containsExactlyInAnyOrder("with\ttab.txt", "with\nnewline.txt")
    }

    @Test
    fun `reports a deleted umlaut file with its real name against real git`() = runTest {
        val realChangeSet = GitChangeSet(OnDiskOperations(), DefaultGitOperationRunner())
        initRepo(tempRepo)
        writeAndCommit(tempRepo, "Grüße.md", "old", "first")
        writeAndCommit(tempRepo, "kept.txt", "kept", "second")
        val from = revParse(tempRepo)
        git(tempRepo, "rm", "Grüße.md")
        git(tempRepo, "commit", "-m", "delete", "--quiet")
        val to = revParse(tempRepo)

        val paths = realChangeSet.changedPaths(tempRepo, from, to)

        assertThat(paths).containsExactly("Grüße.md")
    }

    private fun initRepo(repo: Path) {
        git(repo, "init", "--quiet")
        git(repo, "config", "user.email", "test@example.com")
        git(repo, "config", "user.name", "Test")
        git(repo, "config", "commit.gpgsign", "false")
    }

    private fun writeAndCommit(repo: Path, name: String, content: String, message: String) {
        writeFile(repo, name, content)
        git(repo, "add", "-A")
        git(repo, "commit", "-m", message, "--quiet")
    }

    private fun writeFile(repo: Path, name: String, content: String) {
        val target = repo.resolve(name)
        target.parent?.toFile()?.mkdirs()
        target.toFile().writeText(content)
    }

    private fun revParse(repo: Path): String =
        OnDiskOperations.exec(repo, ProcessBuilder("git", "rev-parse", "HEAD")).trim()

    private fun git(repo: Path, vararg args: String): String =
        OnDiskOperations.exec(repo, ProcessBuilder(listOf("git") + args.toList()))
}
