package com.sprintstart.sprintstartbackend.shared.git

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class GitWorkingTreeTest {
    @TempDir
    lateinit var tempDir: Path

    private val gitRunner = mockk<GitOperationRunner>()
    private val onDiskOperations = OnDiskOperations()

    private fun tree(maxFileSizeBytes: Long = 1_048_576) = GitWorkingTree(
        onDiskOperations = onDiskOperations,
        gitRunner = gitRunner,
        config = GitConfig(ingest = GitIngestConfig(maxFileSizeBytes = maxFileSizeBytes)),
    )

    // ── enumeration ───────────────────────────────────────────────────────────

    @Test
    fun `enumerates tracked files from the index instead of walking the filesystem`() = runTest {
        every { gitRunner.exec(any(), any()) } returns "a.kt\u0000dir/b.kt\u0000"

        val files = tree().trackedFiles(tempDir)

        assertThat(files).containsExactly("a.kt", "dir/b.kt")
        verify {
            gitRunner.exec(any(), match { it.command() == listOf("git", "ls-files", "-z") })
        }
    }

    @Test
    fun `ignores empty entries produced by the trailing terminator`() = runTest {
        every { gitRunner.exec(any(), any()) } returns "a.kt\u0000\u0000"

        assertThat(tree().trackedFiles(tempDir)).containsExactly("a.kt")
    }

    // ── reading ───────────────────────────────────────────────────────────────

    @Test
    fun `returns the text and the hash of the same bytes`() = runTest {
        tempDir.resolve("hello.txt").writeText("hello")

        val read = tree().readFile(tempDir, "hello.txt")

        assertThat(read).isInstanceOf(GitFileRead.Text::class.java)
        val text = read as GitFileRead.Text
        assertThat(text.text).isEqualTo("hello")
        assertThat(text.sha256).isEqualTo(SHA256_OF_HELLO)
    }

    @Test
    fun `never reads a binary file`() = runTest {
        tempDir.resolve("logo.png").writeBytes(byteArrayOf(1, 2, 3))

        assertThat(tree().readFile(tempDir, "logo.png")).isEqualTo(GitFileRead.Binary)
    }

    @Test
    fun `decides binary by the file name rather than by a dotted directory`() = runTest {
        val tree = tree()
        tempDir.resolve("archive.zip").writeText("x")

        assertThat(tree.isBinary("archive.zip")).isTrue()
        assertThat(tree.isBinary("src/release.notes/readme")).isFalse()
        assertThat(tree.isBinary("src/readme.md")).isFalse()
        assertThat(tree.isBinary("Makefile")).isFalse()
    }

    @Test
    fun `reports a file above the size limit without reading it`() = runTest {
        tempDir.resolve("big.json").writeText("12345")

        val read = tree(maxFileSizeBytes = 4).readFile(tempDir, "big.json")

        assertThat(read).isEqualTo(GitFileRead.TooLarge(5))
    }

    @Test
    fun `reports invalid utf-8 instead of replacing the bytes with a placeholder`() = runTest {
        Files.write(tempDir.resolve("latin1.txt"), byteArrayOf(0x48, 0x69, 0xFF.toByte(), 0x0A))

        val read = tree().readFile(tempDir, "latin1.txt")

        assertThat(read).isInstanceOf(GitFileRead.Unreadable::class.java)
        assertThat((read as GitFileRead.Unreadable).reason).contains("UTF-8")
    }

    /**
     * NUL is valid UTF-8, so the strict decode accepts it — but PostgreSQL text columns reject it,
     * and the rejection would surface as a database error in the synchronous file listener,
     * failing the whole files phase and pinning the cursor. Reported as unreadable instead, so the
     * file is recorded as failed and the run moves on.
     */
    @Test
    fun `reports valid utf-8 containing nul bytes instead of poisoning the whole phase`() = runTest {
        tempDir.resolve("nul.txt").writeBytes("hello\u0000world".toByteArray())

        val read = tree().readFile(tempDir, "nul.txt")

        assertThat(read).isInstanceOf(GitFileRead.Unreadable::class.java)
        assertThat((read as GitFileRead.Unreadable).reason).contains("NUL")
    }

    @Test
    fun `reports a file that disappeared between enumeration and reading`() = runTest {
        val read = tree().readFile(tempDir, "gone.txt")

        assertThat(read).isInstanceOf(GitFileRead.Unreadable::class.java)
    }

    private fun Path.writeText(content: String) = Files.writeString(this, content)

    private fun Path.writeBytes(bytes: ByteArray) = Files.write(this, bytes)

    private companion object {
        /** SHA-256 of the ASCII string `hello`. */
        const val SHA256_OF_HELLO = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
    }
}
