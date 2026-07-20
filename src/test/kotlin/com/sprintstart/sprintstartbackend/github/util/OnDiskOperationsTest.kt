package com.sprintstart.sprintstartbackend.github.util

import com.sprintstart.sprintstartbackend.connectors.github.util.OnDiskOperations
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class OnDiskOperationsTest {
    private lateinit var tempDir: Path
    private val onDiskOperations = OnDiskOperations()

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("on-disk-operations-test")
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Nested
    inner class Exec {
        private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

        @Test
        fun `exec returns stdout of successful process`() {
            val command = if (isWindows) listOf("cmd.exe", "/c", "echo hello") else listOf("echo", "hello")
            val result = OnDiskOperations.exec(tempDir, ProcessBuilder(command))
            assertThat(result.trim()).isEqualTo("hello")
        }

        @Test
        fun `exec captures multiline output`() {
            val command = if (isWindows) {
                listOf("cmd.exe", "/c", "echo line1 & echo line2 & echo line3")
            } else {
                listOf("printf", "line1\nline2\nline3")
            }
            val result = OnDiskOperations.exec(tempDir, ProcessBuilder(command))
            assertThat(result.lines().filter { it.isNotBlank() }.map { it.trim() })
                .containsExactly("line1", "line2", "line3")
        }

        @Test
        fun `exec throws RuntimeException on non-zero exit code`() {
            assertThatThrownBy {
                OnDiskOperations.exec(tempDir, ProcessBuilder("git", "status"))
            }.isInstanceOf(RuntimeException::class.java)
                .hasMessageContaining("failed (exit")
        }

        @Test
        fun `exec includes command in exception message`() {
            assertThatThrownBy {
                OnDiskOperations.exec(tempDir, ProcessBuilder("git", "status"))
            }.hasMessageContaining("git status")
        }

        @Test
        fun `exec includes process output in exception message`() {
            val command = if (isWindows) {
                listOf("cmd.exe", "/c", "echo fatal: auth failed & exit 128")
            } else {
                listOf("bash", "-lc", "printf 'fatal: auth failed'; exit 128")
            }
            assertThatThrownBy {
                OnDiskOperations.exec(tempDir, ProcessBuilder(command))
            }.hasMessageContaining("fatal: auth failed")
        }

        @Test
        fun `exec redacts credentials from command in exception message`() {
            assertThatThrownBy {
                OnDiskOperations.exec(
                    tempDir,
                    ProcessBuilder(
                        "git",
                        "clone",
                        "https://x-access-token:secret-token@github.com/owner/repo.git",
                    ),
                )
            }.hasMessageContaining("x-access-token:***@github.com/owner/repo.git")
                .doesNotHaveToString("secret-token")
        }

        @Test
        fun `exec runs command in the provided directory`() {
            // Create a subdirectory and verify exec resolves relative to it
            val subDir = Files.createTempDirectory(tempDir, "subdir")
            val command = if (isWindows) listOf("cmd.exe", "/c", "cd") else listOf("pwd")
            val result = OnDiskOperations.exec(subDir, ProcessBuilder(command))
            assertThat(result.trim()).isEqualTo(subDir.toAbsolutePath().toString())
        }
    }

    @Nested
    inner class GitCommitsAfter {
        @Test
        fun `gitCommitsAfter includes after flag with formatted instant`() {
            val instant = Instant.parse("2024-01-15T10:30:00Z")
            val command = onDiskOperations.gitCommitsAfter(instant)
            assertThat(command.command()).contains("--after=2024-01-15T10:30:00Z")
        }

        @Test
        fun `gitCommitsAfter includes pretty format flag`() {
            val command = onDiskOperations.gitCommitsAfter(Instant.now())
            assertThat(command.command()).contains("--pretty=format:%cI - %H - %an - %s")
        }

        @Test
        fun `gitCommitsAfter produces valid git log command`() {
            val command = onDiskOperations.gitCommitsAfter(Instant.now())
            assertThat(command.command().first()).isEqualTo("git")
            assertThat(command.command()).contains("log")
        }
    }
}
