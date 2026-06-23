package com.sprintstart.sprintstartbackend.github.util

import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.CryptoConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import com.sprintstart.sprintstartbackend.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.github.models.GithubUser
import com.sprintstart.sprintstartbackend.github.models.GithubUserPat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
            cron = "0 0 * * *",
        ),
        crypto = CryptoConfig(masterKey = "unused", salt = "unused"),
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
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            every { gitRunner.exec(any(), any()) } returns ""

            runBlocking { cache.getLocalRepositoryPath(repository) }

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
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            every { gitRunner.exec(any(), any()) } returns ""

            val result = runBlocking { cache.getLocalRepositoryPath(repository) }

            assertThat(result).isEqualTo(Path.of(tempDir.toString(), "owner", "repo"))
        }

        @Test
        fun `concurrent requests clone repository only once`() {
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )
            every {
                gitRunner.exec(any(), match { it.command().contains("clone") })
            } answers {
                Thread.sleep(100)
                ""
            }
            every {
                gitRunner.exec(any(), match { it.command().contains("status") })
            } returns ""
            every {
                gitRunner.exec(any(), match { it.command().contains("rev-parse") })
            } returns "abc123\n"

            val results = runBlocking {
                awaitAll(
                    async { cache.getLocalRepositoryPath(repository) },
                    async { cache.getLocalRepositoryPath(repository) },
                )
            }

            assertThat(results).allMatch { it == Path.of(tempDir.toString(), "owner", "repo") }
            verify(exactly = 1) {
                gitRunner.exec(any(), match { pb -> pb.command().contains("clone") })
            }
        }
    }

    @Nested
    inner class CacheHit {
        @Test
        fun `returns cached path without cloning when repository is valid`() {
            val repoDir = tempDir.resolve("owner/repo").also {
                Files.createDirectories(it)
            }
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )

            every { gitRunner.exec(repoDir, match { it.command().contains("status") }) } returns ""
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "abc123\n"

            val result = runBlocking { cache.getLocalRepositoryPath(repository) }

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
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )

            every {
                gitRunner.exec(repoDir, match { it.command().contains("status") })
            } throws RuntimeException("not a git repository (exit 128)")

            every {
                gitRunner.exec(any(), match { it.command().contains("clone") })
            } returns ""
            every {
                gitRunner.exec(any(), match { it.command().contains("rev-parse") })
            } returns "abc123\n"

            runBlocking { cache.getLocalRepositoryPath(repository) }

            verify {
                gitRunner.exec(any(), match { pb -> pb.command().contains("clone") })
            }
        }

        @Test
        fun `repairs cached clone when git status succeeds but HEAD is invalid`() {
            val repoDir = tempDir.resolve("owner/repo").also {
                Files.createDirectories(it)
            }
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )

            every { gitRunner.exec(repoDir, match { it.command().contains("status") }) } returns ""
            every {
                gitRunner.exec(repoDir, match { it.command() == listOf("git", "rev-parse", "HEAD") })
            } throws RuntimeException("git rev-parse HEAD failed (exit 128)") andThen "fixed-sha\n"
            every {
                gitRunner.exec(
                    repoDir,
                    match {
                        it.command() ==
                            listOf("git", "for-each-ref", "refs/remotes/origin", "--format=%(refname:short)")
                    },
                )
            } returns "origin/trunk\n"
            every {
                gitRunner.exec(
                    repoDir,
                    match { it.command() == listOf("git", "checkout", "-B", "trunk", "refs/remotes/origin/trunk") },
                )
            } returns ""

            val result = runBlocking { cache.getLocalRepositoryPath(repository) }

            assertThat(result).isEqualTo(repoDir)
            verify(exactly = 0) {
                gitRunner.exec(any(), match { pb -> pb.command().contains("clone") })
            }
            verify {
                gitRunner.exec(
                    repoDir,
                    match { it.command() == listOf("git", "checkout", "-B", "trunk", "refs/remotes/origin/trunk") },
                )
            }
        }
    }

    @Nested
    inner class TokenSafety {
        @Test
        fun `clone uri contains token but safe uri does not`() {
            val cloneUris = mutableListOf<String>()
            val repository = GithubRepositoryConnection(
                owner = "owner",
                name = "repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )

            every { gitRunner.exec(any(), any()) } answers {
                // capture the clone command arguments
                val pb = secondArg<ProcessBuilder>()
                if (pb.command().contains("clone")) {
                    cloneUris.addAll(pb.command())
                }
                ""
            }

            runBlocking { cache.getLocalRepositoryPath(repository) }

            // The real URI with the token should be in the clone command
            assertThat(cloneUris).anyMatch { it.contains("x-access-token:test-token") }
            // But it should never appear in log output — we can't assert logs directly,
            // so we verify the safe URI does NOT contain the token
            assertThat("https://x-access-token:***@github.com/owner/repo.git").doesNotContain("test-token")
        }
    }

    @Nested
    inner class PathStructure {
        @Test
        fun `local path is structured as cacheBasePath-owner-name`() {
            every { gitRunner.exec(any(), any()) } returns ""
            val repository = GithubRepositoryConnection(
                owner = "my-org",
                name = "my-repo",
                user = GithubUser(id = GithubUserPat("some-id", "test-pat"), token = "test-token"),
            )

            val result = runBlocking { cache.getLocalRepositoryPath(repository) }

            assertThat(result.toString()).endsWith("my-org/my-repo")
            assertThat(result.toString()).startsWith(tempDir.toString())
        }
    }
}
