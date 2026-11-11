package com.sprintstart.sprintstartbackend.shared.git

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

/** Reads real history, because the record format is Git's and a mocked output proves only our reading of it. */
class GitLogRealGitTest {
    @TempDir
    lateinit var repo: Path

    @BeforeEach
    fun initRepository() {
        git("init", "--quiet")
        git("config", "user.email", "ada@example.com")
        git("config", "user.name", "Ada Lovelace")
        git("config", "commit.gpgsign", "false")
        git("config", "core.autocrlf", "false")
    }

    private fun logLimitedTo(maxFilesPerCommit: Int = 200) =
        GitLog(
            OnDiskOperations(),
            DefaultGitOperationRunner(),
            GitConfig(ingest = GitIngestConfig(maxFilesPerCommit = maxFilesPerCommit)),
        )

    private suspend fun commits(maxFilesPerCommit: Int = 200) =
        logLimitedTo(maxFilesPerCommit).commits(repo, sinceRevision = null)

    private fun listed(commit: GitCommit) = (commit.files as CommitFiles.Listed).files

    private fun commitFile(name: String, content: String, message: String) {
        write(name, content)
        git("add", "-A")
        git("commit", "-m", message, "--quiet")
    }

    private fun write(name: String, content: String) {
        val target = repo.resolve(name)
        target.parent?.toFile()?.mkdirs()
        target.toFile().writeText(content)
    }

    private fun git(vararg args: String): String = OnDiskOperations.exec(repo, ProcessBuilder(listOf("git") + args))

    @Test
    fun `reads the author name and email and the files of a commit`() = runTest {
        commitFile("src/A.kt", "one\ntwo\nthree\n", "Add A")

        val commit = commits().single()

        assertThat(commit.authorName).isEqualTo("Ada Lovelace")
        assertThat(commit.authorEmail).isEqualTo("ada@example.com")
        assertThat(commit.subject).isEqualTo("Add A")
        assertThat(listed(commit)).containsExactly(CommitFile("src/A.kt", 3, 0, null))
    }

    @Test
    fun `counts added and removed lines of a modified file`() = runTest {
        commitFile("a.txt", "one\ntwo\nthree\n", "first")
        commitFile("a.txt", "one\nTWO\nthree\nfour\n", "second")

        assertThat(listed(commits().first())).containsExactly(CommitFile("a.txt", 2, 1, null))
    }

    @Test
    fun `reports a moved and edited file as a rename`() = runTest {
        commitFile("a.txt", "one\ntwo\nthree\nfour\nfive\n", "first")
        git("mv", "a.txt", "moved.txt")
        write("moved.txt", "one\ntwo\nthree\nfour\nfive\nsix\n")
        git("add", "-A")
        git("commit", "-m", "move", "--quiet")

        assertThat(listed(commits().first())).containsExactly(CommitFile("moved.txt", 1, 0, renamedFrom = "a.txt"))
    }

    @Test
    fun `keeps a path with a space and a non ASCII name`() = runTest {
        commitFile("dir with space/Grüße.md", "hello\n", "umlaut")

        assertThat(listed(commits().single()).single().path).isEqualTo("dir with space/Grüße.md")
    }

    @Test
    fun `keeps a binary file with zero line counts`() = runTest {
        val target = repo.resolve("logo.bin")
        target.toFile().writeBytes(byteArrayOf(0, 1, 2, 3, 0, 4))
        git("add", "-A")
        git("commit", "-m", "binary", "--quiet")

        assertThat(listed(commits().single())).containsExactly(CommitFile("logo.bin", 0, 0, null))
    }

    @Test
    fun `lists no files for a merge commit`() = runTest {
        commitFile("base.txt", "base\n", "base")
        git("switch", "-c", "side", "--quiet")
        commitFile("side.txt", "side\n", "side work")
        git("switch", "-", "--quiet")
        commitFile("main.txt", "main\n", "main work")
        git("merge", "--no-ff", "side", "-m", "merge side", "--quiet")

        val all = commits()

        assertThat(all.first().subject).isEqualTo("merge side")
        assertThat(all.first().files).isEqualTo(CommitFiles.Listed(emptyList()))
        assertThat(all.drop(1).map { it.subject }).contains("side work", "main work")
        assertThat(listed(all.first { it.subject == "side work" }).map { it.path }).containsExactly("side.txt")
    }

    @Test
    fun `reports a commit above the limit without listing its files`() = runTest {
        write("a.txt", "a\n")
        write("b.txt", "b\n")
        write("c.txt", "c\n")
        git("add", "-A")
        git("commit", "-m", "bulk", "--quiet")

        assertThat(commits(maxFilesPerCommit = 2).single().files).isEqualTo(CommitFiles.TooMany(3))
    }

    @Test
    fun `reads only the commits after a cursor`() = runTest {
        commitFile("a.txt", "a\n", "first")
        val cursor = git("rev-parse", "HEAD").trim()
        commitFile("b.txt", "b\n", "second")

        val after = logLimitedTo().commits(repo, sinceRevision = cursor)

        assertThat(after.map { it.subject }).containsExactly("second")
    }
}
