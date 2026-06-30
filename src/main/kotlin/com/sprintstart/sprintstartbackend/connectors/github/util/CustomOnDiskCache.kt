package com.sprintstart.sprintstartbackend.connectors.github.util

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

/**
 * Interface for running Git CLI commands. Used for mocking in tests.
 */
interface GitOperationRunner {
    fun exec(path: Path, op: ProcessBuilder): String
}

/**
 * Default implementation of [GitOperationRunner] that uses [OnDiskOperations].
 */
@Service
class DefaultGitOperationRunner : GitOperationRunner {
    override fun exec(path: Path, op: ProcessBuilder): String =
        OnDiskOperations.exec(path, op)
}

/**
 * On-disk cache for GitHub repositories.
 *
 * Maintains a local clone of each repository under [cacheBasePath], keyed by owner and name
 * (e.g. `/repos/SprintStartProject/sprintstart-backend`). On first access the repository is cloned from GitHub;
 * subsequent accesses return the cached path immediately without any network calls.
 *
 * Designed to be used alongside [OnDiskOperations], which handles diffing and file reading.
 *
 * Cache validity is verified by running `git status` on the local directory rather than relying
 * on directory existence alone, so interrupted or corrupted clones are detected and re-cloned
 * automatically.
 *
 * @property cacheBasePath Root directory for all cached repositories.
 *                         Defaults to `/repos`, which maps to the Kubernetes PVC mount.
 *                         Override via `sprintstart.github.cache-path` for local development.
 */
@Service
class CustomOnDiskCache(
    @Value("\${sprintstart.github.cache-path:/repos}")
    private val cacheBasePath: String,
    private val applicationConfig: ApplicationConfig,
    private val onDiskOperations: OnDiskOperations,
    private val gitRunner: GitOperationRunner,
) {
    private val logger = LoggerFactory.getLogger(CustomOnDiskCache::class.java)
    private val repositoryLocks = ConcurrentHashMap<Path, Mutex>()

    /**
     * Returns the local filesystem path for the given repository, cloning it first if not cached.
     *
     * @param repository The repository to get the path for.
     * @return Absolute path to the local clone, ready for filesystem operations
     */
    suspend fun getLocalRepositoryPath(repository: GithubRepositoryConnection): Path {
        val localFsPath = Path.of(cacheBasePath, repository.owner, repository.name)
        val remoteUri = buildRemoteUri(repository.owner, repository.name, repository.user.token)
        val safeUri = buildRemoteUri(repository.owner, repository.name, "***")

        return getLocalRepositoryPath(localFsPath, remoteUri, safeUri)
    }

    /**
     * Returns the path to the local GitHub repository copy.
     *
     * This function returns the path to the local copy of a given
     * GitHub repository, caching it if not happened already.
     *
     * @param localFsPath The path to the local copy of the GitHub repository.
     * @param remoteUri The remote uri of the repository, to clone from if needed.
     * @param safeUri The remote uri, but safe for printing, e.g. without credentials.
     */
    private suspend fun getLocalRepositoryPath(localFsPath: Path, remoteUri: String, safeUri: String): Path {
        val repositoryLock = repositoryLocks.computeIfAbsent(localFsPath.normalize()) { Mutex() }

        repositoryLock.withLock {
            if (!isCached(localFsPath)) {
                logger.info("Cache miss for $safeUri — cloning")
                cloneRepository(localFsPath, remoteUri)
            } else {
                logger.info("Cache hit for $safeUri")
            }
        }

        return localFsPath
    }

    /**
     * Checks whether a valid local clone exists at [path].
     *
     * Presence of the directory alone is not sufficient — a partial or interrupted clone can leave
     * a broken `.git` directory behind. Validity is confirmed by running `git status`; a non-zero
     * exit code is treated as a cache miss.
     *
     * @param path The path of the repository to check if exists locally.
     */
    private suspend fun isCached(path: Path): Boolean {
        if (!path.exists()) return false
        return withContext(Dispatchers.IO) {
            try {
                gitRunner.exec(path, onDiskOperations.gitStatus())
                ensureRepositoryCheckout(path)
                true
            } catch (@Suppress("SwallowedException") e: RuntimeException) {
                false
            }
        }
    }

    /**
     * Clones the remote repository into [localFsPath].
     *
     * Any existing content at [localFsPath] is deleted first to guarantee a clean clone,
     * which handles the case of a previously interrupted clone leaving partial state behind.
     *
     * The [remoteUri] contains the auth token inline and is never logged.
     * Use [safeUri] (token replaced with `***`) for all log output.
     *
     * @param localFsPath The path to the local copy of the GitHub repository.
     * @param remoteUri The uri to clone the repository from, if not already cached.
     */
    private suspend fun cloneRepository(localFsPath: Path, remoteUri: String) {
        withContext(Dispatchers.IO) {
            localFsPath.toFile().deleteRecursively()
            localFsPath.toFile().mkdirs()

            gitRunner.exec(localFsPath, onDiskOperations.gitClone(remoteUri, localFsPath.absolutePathString()))
            ensureRepositoryCheckout(localFsPath)
        }
    }

    /**
     * Repairs clones whose remote default branch is invalid by checking out the first available remote branch.
     *
     * Some repositories are non-empty but advertise a stale remote `HEAD`, which leaves the clone
     * without a resolvable local `HEAD`. In that state `git status` still succeeds, so cache validation
     * must do a stronger check than repository presence alone.
     *
     * Empty repositories are treated as valid even though `HEAD` cannot be resolved yet.
     */
    private fun ensureRepositoryCheckout(localFsPath: Path) {
        try {
            gitRunner.exec(localFsPath, onDiskOperations.gitRevParse())
            return
        } catch (@Suppress("SwallowedException") e: RuntimeException) {
            val remoteBranch = findFirstRemoteBranch(localFsPath) ?: return
            logger.info("Repairing cached clone by checking out origin/{}", remoteBranch)
            gitRunner.exec(localFsPath, onDiskOperations.gitCheckoutRemoteBranch(remoteBranch))
            gitRunner.exec(localFsPath, onDiskOperations.gitRevParse())
        }
    }

    private fun findFirstRemoteBranch(localFsPath: Path): String? {
        val branches = gitRunner
            .exec(localFsPath, onDiskOperations.gitRemoteBranches())
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() }
            .filter { it != "origin/HEAD" }
            .map { it.removePrefix("origin/") }
            .toList()

        return branches.firstOrNull()
    }

    private fun buildRemoteUri(owner: String, name: String, token: String): String =
        URI(
            "https",
            "x-access-token:$token",
            "github.com",
            -1,
            "/$owner/$name.git",
            null,
            null,
        ).toASCIIString()
}
