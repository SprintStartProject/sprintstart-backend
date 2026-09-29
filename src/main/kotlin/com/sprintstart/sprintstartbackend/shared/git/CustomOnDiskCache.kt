package com.sprintstart.sprintstartbackend.shared.git

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

/**
 * On-disk cache of remote Git repositories, shared by every repository connector.
 *
 * Keeps one local clone per hosted repository under `cacheBasePath/<host>/<namespace>/<name>`
 * (e.g. `/repos/github.com/SprintStartProject/sprintstart-backend`). The host is part of the
 * location because the same namespace and name can exist on several providers at once: without
 * it, a Bitbucket `workspace/slug` would be served an existing GitHub clone.
 *
 * On first access the repository is cloned over HTTPS with the credentials from
 * [GitRepositoryCoordinates]; later accesses return the cached path without any network call.
 * Which connector is asking is not a concept here, which is why this class builds the clone URL
 * from the coordinates rather than from a connection entity.
 *
 * Cache validity is verified by running `git status` on the local directory rather than relying on
 * directory existence alone, so interrupted or corrupted clones are detected and re-cloned
 * automatically. A clone whose `HEAD` is invalid is repaired by checking out the first remote
 * branch rather than being thrown away.
 *
 * Designed to be used alongside [OnDiskOperations], which handles diffing and file reading.
 *
 * @property cacheBasePath Root directory for all cached repositories, shared by all connectors.
 *                         Defaults to `/repos`, which maps to the Kubernetes PVC mount.
 *                         Override via `sprintstart.git.cache-path` for local development.
 */
@Service
class CustomOnDiskCache(
    private val config: GitConfig,
    private val onDiskOperations: OnDiskOperations,
    private val gitRunner: GitOperationRunner,
) : GitRepositoryCache {
    private val logger = LoggerFactory.getLogger(CustomOnDiskCache::class.java)
    private val repositoryLocks = ConcurrentHashMap<Path, Mutex>()

    /**
     * Returns the local filesystem path for the given repository, cloning it first if not cached.
     *
     * @param coordinates The repository to materialize locally, including its credentials.
     * @return Absolute path to the local clone, ready for filesystem operations
     */
    override suspend fun getLocalRepositoryPath(coordinates: GitRepositoryCoordinates): Path {
        val localFsPath = coordinates.namespacePath
            .fold(Path.of(config.cachePath, coordinates.host)) { path, segment -> path.resolve(segment) }
            .resolve(coordinates.name)
        val remoteUri = buildRemoteUri(coordinates, coordinates.secret)
        val safeUri = buildRemoteUri(coordinates, MASKED_SECRET)

        return getLocalRepositoryPath(localFsPath, remoteUri, safeUri)
    }

    /**
     * Returns the path to the local copy of a repository, cloning it if that has not happened yet.
     *
     * @param localFsPath The path to the local copy of the repository.
     * @param remoteUri The remote uri of the repository to clone from if needed.
     * @param safeUri The remote uri, but safe for printing, e.g. without credentials.
     */
    private suspend fun getLocalRepositoryPath(localFsPath: Path, remoteUri: String, safeUri: String): Path {
        val repositoryLock = repositoryLocks.computeIfAbsent(localFsPath.normalize()) { Mutex() }

        repositoryLock.withLock {
            if (!isCached(localFsPath)) {
                logger.info("Cache miss for $safeUri — cloning")
                cloneRepository(localFsPath, remoteUri)
            } else {
                logger.info("Cache hit for $safeUri — refreshing remote URL")
                refreshRemoteUrl(localFsPath, remoteUri)
            }
        }

        return localFsPath
    }

    /**
     * Re-points `origin` at the current credentials.
     *
     * The clone's remote URL still carries whatever token was current when it was cloned. Without
     * this, a rotated token keeps failing fetches with the old one until the cache is wiped. A
     * local config write only — no network — executed under the repository lock like the clone.
     *
     * The [remoteUri] contains the auth token inline and is never logged.
     *
     * @param localFsPath The local clone whose remote URL should be refreshed.
     * @param remoteUri The current remote URI, with the current credentials embedded.
     */
    private suspend fun refreshRemoteUrl(localFsPath: Path, remoteUri: String) {
        withContext(Dispatchers.IO) {
            gitRunner.exec(localFsPath, onDiskOperations.gitSetRemoteUrl(remoteUri))
        }
    }

    /**
     * Checks whether a valid local clone exists at [path].
     *
     * Presence of the directory alone is not enough — a partial or interrupted clone can leave
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
     *
     * @param localFsPath The path to the local copy of the repository.
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
     * Ensures that the provided Git repository at [localFsPath] is correctly checked out.
     *
     * This method verifies the state of the local Git repository by executing a `git rev-parse` command.
     * If the repository is in an inconsistent or broken state, it attempts to repair it by checking out the
     * first remote branch found (if available) and running the necessary commands to re-establish the repository's
     * validity.
     *
     * @param localFsPath The path to the local copy of the Git repository.
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

    /**
     * Finds the first available remote Git branch for the given local file system path.
     *
     * @param localFsPath the local file system path where the Git repository is located.
     * @return the name of the first remote branch found, or null if no branches are available.
     */
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

    /**
     * Constructs the HTTPS remote URI of a repository, with the credentials embedded.
     *
     * The result contains [secret] in cleartext and must never be logged; passing [MASKED_SECRET]
     * instead produces the URI that is safe to print.
     *
     * @param coordinates The repository to address.
     * @param secret The secret to embed, either the real one or [MASKED_SECRET].
     * @return The remote URI in ASCII format, as `git clone` accepts it.
     */
    private fun buildRemoteUri(coordinates: GitRepositoryCoordinates, secret: String): String =
        URI(
            "https",
            "${coordinates.username}:$secret",
            coordinates.host,
            -1,
            "/${coordinates.namespacePath.joinToString("/")}/${coordinates.name}.git",
            null,
            null,
        ).toASCIIString()

    private companion object {
        /** Placeholder that replaces a credential wherever the URI is displayed. */
        const val MASKED_SECRET = "***"
    }
}
