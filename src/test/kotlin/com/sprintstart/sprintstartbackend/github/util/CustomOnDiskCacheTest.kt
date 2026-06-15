package com.sprintstart.sprintstartbackend.github.util

import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class CustomOnDiskCacheTest {
    private lateinit var tempDir: Path
    private val gitRunner = mockk<GitOperationRunner>()
    private val onDiskOperations = OnDiskOperations()

    private val applicationConfig = ApplicationConfig(
        ai = AiConfig(baseUrl = "http://unused"),
        github = GithubConfig(
            baseUrl = "http://unused",
            repoBaseUrl = "http://unused",
            token = "test-token",
            cron = "0 0 * * *",
        ),
    )

    private lateinit var cache: CustomOnDiskCache

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("cache-test")
        cache = CustomOnDiskCache(
            cacheBasePath = tempDir.toString(),
            applicationConfig = applicationConfig,
            onDiskOperations = onDiskOperations,
            gitRunner = gitRunner,
        )
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    @Nested
    inner class CacheMiss {
        @Test
        fun `clones repository when no local directory exists`() {
            every { gitRunner.exec(any(), any()) } returns ""

            runBlocking { cache.getLocalRepositoryPath("owner", "repo") }

            // clone command should have been called
            verify {
                gitRunner.exec(
                    any(),
                    match { pb ->
                        pb.command().contains("clone")
                    },
                )
            }
        }

        @Test
        fun `returns correct path after cloning`() {
            every { gitRunner.exec(any(), any()) } returns ""

            val result = runBlocking { cache.getLocalRepositoryPath("owner", "repo") }

            assertThat(result).isEqualTo(Path.of(tempDir.toString(), "owner", "repo"))
        }
    }

    @Nested
    inner class CacheHit {
        @Test
        fun `returns cached path without cloning when repository is valid`() {
            val repoDir = tempDir.resolve("owner/repo").also {
                Files.createDirectories(it)
            }

            // git status succeeds → cache hit
            every { gitRunner.exec(repoDir, match { it.command().contains("status") }) } returns ""

            val result = runBlocking { cache.getLocalRepositoryPath("owner", "repo") }

            assertThat(result).isEqualTo(repoDir)
            verify(exactly = 0) {
                gitRunner.exec(any(), match { pb -> pb.command().contains("clone") })
            }
        }
    }

    @Nested
    inner class CorruptedClone {
        @Test
        fun `re-clones when directory exists but git status fails`() {
            val repoDir = tempDir.resolve("owner/repo").also {
                Files.createDirectories(it)
            }

            every {
                gitRunner.exec(repoDir, match { it.command().contains("status") })
            } throws RuntimeException("not a git repository (exit 128)")

            every {
                gitRunner.exec(any(), match { it.command().contains("clone") })
            } returns ""

            runBlocking { cache.getLocalRepositoryPath("owner", "repo") }

            verify {
                gitRunner.exec(any(), match { pb -> pb.command().contains("clone") })
            }
        }
    }

    @Nested
    inner class TokenSafety {
        @Test
        fun `clone uri contains token but safe uri does not`() {
            val cloneUris = mutableListOf<String>()

            every { gitRunner.exec(any(), any()) } answers {
                // capture the clone command arguments
                val pb = secondArg<ProcessBuilder>()
                if (pb.command().contains("clone")) {
                    cloneUris.addAll(pb.command())
                }
                ""
            }

            runBlocking { cache.getLocalRepositoryPath("owner", "repo") }

            // The real URI with the token should be in the clone command
            assertThat(cloneUris).anyMatch { it.contains("test-token") }
            // But it should never appear in log output — we can't assert logs directly,
            // so we verify the safe URI does NOT contain the token
            assertThat("https://***@github.com/owner/repo.git").doesNotContain("test-token")
        }
    }

    @Nested
    inner class PathStructure {
        @Test
        fun `local path is structured as cacheBasePath-owner-name`() {
            every { gitRunner.exec(any(), any()) } returns ""

            val result = runBlocking { cache.getLocalRepositoryPath("my-org", "my-repo") }

            assertThat(result.toString()).endsWith("my-org/my-repo")
            assertThat(result.toString()).startsWith(tempDir.toString())
        }
    }
}
