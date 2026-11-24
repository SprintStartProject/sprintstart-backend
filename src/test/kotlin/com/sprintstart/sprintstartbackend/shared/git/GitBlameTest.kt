package com.sprintstart.sprintstartbackend.shared.git

import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class GitBlameParserTest {
    private fun block(email: String, text: String) =
        "0123456789abcdef0123456789abcdef01234567 1 1 1\nauthor Someone\nauthor-mail <$email>\n" +
            "summary Something\nfilename f.txt\n\t$text\n"

    @Test
    fun `counts the lines of each author, lower-cased, without the brackets`() {
        val output = block("Ada@X.org", "a") + block("ben@x.org", "b") + block("ada@x.org", "c")

        assertThat(GitBlameParser.parse(output)).isEqualTo(mapOf("ada@x.org" to 2, "ben@x.org" to 1))
    }

    @Test
    fun `the text of a line cannot be taken for a header`() {
        val output = block("ada@x.org", "author-mail <ben@x.org>")

        assertThat(GitBlameParser.parse(output)).isEqualTo(mapOf("ada@x.org" to 1))
    }

    @Test
    fun `lines without an author and empty output count nothing`() {
        assertThat(GitBlameParser.parse(block("", "a"))).isEmpty()
        assertThat(GitBlameParser.parse("")).isEmpty()
    }
}

class GitBlameTest {
    private val runner = mockk<GitOperationRunner>()
    private val blame = GitBlame(OnDiskOperations(), runner)

    @Test
    fun `reads the authors from what git printed`() {
        every { runner.exec(any(), any()) } returns "author-mail <ada@x.org>\n\ta\n"

        assertThat(blame.authorLines(Path.of("/repo"), "a.kt")).isEqualTo(mapOf("ada@x.org" to 1))
    }

    @Test
    fun `a file git cannot blame has no authors instead of an error`() {
        every { runner.exec(any(), any()) } throws IllegalStateException("fatal: no such path")

        assertThat(blame.authorLines(Path.of("/repo"), "gone.kt")).isEmpty()
    }
}

/** Reads real blame, because its format is Git's and a mocked output proves only our reading of it. */
class GitBlameRealGitTest {
    @TempDir
    lateinit var repo: Path

    @BeforeEach
    fun initRepository() {
        git("init", "--quiet")
        git("config", "commit.gpgsign", "false")
        git("config", "core.autocrlf", "false")
    }

    private fun git(vararg args: String): String = OnDiskOperations.exec(repo, ProcessBuilder(listOf("git") + args))

    private fun commit(email: String, content: String) {
        repo.resolve("a.txt").toFile().writeText(content)
        git("add", "-A")
        git("-c", "user.email=$email", "-c", "user.name=X", "commit", "-m", "change", "--quiet")
    }

    private val blame = GitBlame(OnDiskOperations(), DefaultGitOperationRunner())

    @Test
    fun `each line belongs to the author who last wrote it`() {
        commit("ada@x.org", "one\ntwo\nthree\n")
        commit("ben@x.org", "one\nTWO\nthree\nfour\n")

        assertThat(blame.authorLines(repo, "a.txt")).isEqualTo(mapOf("ada@x.org" to 2, "ben@x.org" to 2))
    }

    @Test
    fun `a change of whitespace does not take the lines from their author`() {
        commit("ada@x.org", "if (x) {\n  y()\n}\n")
        commit("ben@x.org", "if (x) {\n        y()\n}\n")

        assertThat(blame.authorLines(repo, "a.txt")).isEqualTo(mapOf("ada@x.org" to 3))
    }

    @Test
    fun `a file that is not tracked has no authors`() {
        commit("ada@x.org", "one\n")

        assertThat(blame.authorLines(repo, "missing.txt")).isEmpty()
    }
}
