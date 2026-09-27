package com.sprintstart.sprintstartbackend.shared.git

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

    private lateinit var cache: CustomOnDiskCache

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("cache-test")
        cache = CustomOnDiskCache(
            config = GitConfig(cachePath = tempDir.toString()),
            onDiskOperations = onDiskOperations,
            gitRunner = gitRunner,
        )
    }

    @AfterEach
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private fun githubCoordinates(
        namespace: String = "owner",
        name: String = "repo",
        secret: String = "test-token",
    ) = GitRepositoryCoordinates(
        host = "github.com",
        namespace = namespace,
        name = name,
        username = "x-access-token",
        secret = secret,
    )

    private fun githubPath(namespace: String = "owner", name: String = "repo"): Path =
        Path.of(tempDir.toString(), "github.com", namespace, name)

    private fun cloneCommands(): List<List<String>> = mutableListOf<List<String>>().also { commands ->
        every { gitRunner.exec(any(), any()) } answers {
            val pb = secondArg<ProcessBuilder>()
            if (pb.command().contains("clone")) commands.add(pb.command())
            ""
        }
    }

    @Nested
    inner class CacheMiss {
        @Test
        fun `clones repository when no local directory exists`() {
            every { gitRunner.exec(any(), any()) } returns ""

            runBlocking { cache.getLocalRepositoryPath(githubCoordinates()) }

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

            val result = runBlocking { cache.getLocalRepositoryPath(githubCoordinates()) }

            assertThat(result).isEqualTo(githubPath())
        }

        @Test
        fun `concurrent requests clone repository only once`() {
            val coordinates = githubCoordinates()
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
                    async { cache.getLocalRepositoryPath(coordinates) },
                    async { cache.getLocalRepositoryPath(coordinates) },
                )
            }

            assertThat(results).allMatch { it == githubPath() }
            verify(exactly = 1) {
                gitRunner.exec(any(), match { pb -> pb.command().contains("clone") })
            }
        }
    }

    @Nested
    inner class CacheHit {
        @Test
        fun `returns cached path without cloning when repository is valid`() {
            val repoDir = githubPath().also {
                Files.createDirectories(it)
            }

            every { gitRunner.exec(repoDir, match { it.command().contains("status") }) } returns ""
            every { gitRunner.exec(repoDir, match { it.command().contains("rev-parse") }) } returns "abc123\n"

            val result = runBlocking { cache.getLocalRepositoryPath(githubCoordinates()) }

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
            val repoDir = githubPath().also {
                Files.createDirectories(it)
            }

            every {
                gitRunner.exec(repoDir, match { it.command().contains("status") })
            } throws RuntimeException("not a git repository (exit 128)")

            every {
                gitRunner.exec(any(), match { it.command().contains("clone") })
            } returns ""
            every {
                gitRunner.exec(any(), match { it.command().contains("rev-parse") })
            } returns "abc123\n"

            runBlocking { cache.getLocalRepositoryPath(githubCoordinates()) }

            verify {
                gitRunner.exec(any(), match { pb -> pb.command().contains("clone") })
            }
        }

        @Test
        fun `repairs cached clone when git status succeeds but HEAD is invalid`() {
            val repoDir = githubPath().also {
                Files.createDirectories(it)
            }

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

            val result = runBlocking { cache.getLocalRepositoryPath(githubCoordinates()) }

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
    inner class CloneUri {
        @Test
        fun `clone uri carries the credentials of the coordinates`() {
            val cloneUris = mutableListOf<String>()
            every { gitRunner.exec(any(), any()) } answers {
                val pb = secondArg<ProcessBuilder>()
                if (pb.command().contains("clone")) cloneUris.addAll(pb.command())
                ""
            }

            runBlocking { cache.getLocalRepositoryPath(githubCoordinates()) }

            assertThat(cloneUris).anyMatch { it.contains("x-access-token:test-token") }
            assertThat(cloneUris).anyMatch { it.contains("@github.com/owner/repo.git") }
        }

        @Test
        fun `clone uri of a bitbucket repository uses the bitbucket host and static user name`() {
            val cloneUris = mutableListOf<String>()
            every { gitRunner.exec(any(), any()) } answers {
                val pb = secondArg<ProcessBuilder>()
                if (pb.command().contains("clone")) cloneUris.addAll(pb.command())
                ""
            }

            val coordinates = GitRepositoryCoordinates(
                host = "bitbucket.org",
                namespace = "sprintstart",
                name = "sprintstart-backend",
                username = "x-bitbucket-api-token-auth",
                secret = "api-token",
            )

            runBlocking { cache.getLocalRepositoryPath(coordinates) }

            assertThat(cloneUris)
                .anyMatch { it.contains("x-bitbucket-api-token-auth:api-token") }
            assertThat(cloneUris).anyMatch { it.contains("@bitbucket.org/sprintstart/sprintstart-backend.git") }
        }
    }

    @Nested
    inner class PathStructure {
        @Test
        fun `local path is structured as cacheBasePath-host-namespace-name`() {
            every { gitRunner.exec(any(), any()) } returns ""

            val result = runBlocking { cache.getLocalRepositoryPath(githubCoordinates("my-org", "my-repo")) }

            assertThat(result.toString().replace("\\", "/")).endsWith("github.com/my-org/my-repo")
            assertThat(result.toString()).startsWith(tempDir.toString())
        }

        @Test
        fun `repositories of different providers never share a directory`() {
            val cloneUris = cloneCommands()
            val github = githubCoordinates()
            val bitbucket = GitRepositoryCoordinates(
                host = "bitbucket.org",
                namespace = github.namespace,
                name = github.name,
                username = "x-bitbucket-api-token-auth",
                secret = "api-token",
            )

            val githubPath = runBlocking { cache.getLocalRepositoryPath(github) }
            val bitbucketPath = runBlocking { cache.getLocalRepositoryPath(bitbucket) }

            assertThat(githubPath).isNotEqualTo(bitbucketPath)
            assertThat(cloneUris).hasSize(2)
        }
    }
}
