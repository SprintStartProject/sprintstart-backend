package com.sprintstart.sprintstartbackend.github.util

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
        @Test
        fun `exec returns stdout of successful process`() {
            val result = OnDiskOperations.exec(tempDir, ProcessBuilder("echo", "hello"))
            assertThat(result.trim()).isEqualTo("hello")
        }

        @Test
        fun `exec captures multiline output`() {
            val result = OnDiskOperations.exec(tempDir, ProcessBuilder("printf", "line1\nline2\nline3"))
            assertThat(result.lines().filter { it.isNotBlank() }).containsExactly("line1", "line2", "line3")
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
        fun `exec runs command in the provided directory`() {
            // Create a subdirectory and verify exec resolves relative to it
            val subDir = Files.createTempDirectory(tempDir, "subdir")
            val result = OnDiskOperations.exec(subDir, ProcessBuilder("pwd"))
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
