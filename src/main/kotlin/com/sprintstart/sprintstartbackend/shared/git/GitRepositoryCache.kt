package com.sprintstart.sprintstartbackend.shared.git

import java.nio.file.Path

/**
 * Resolves the local clone of a hosted repository, creating it when it is not cached yet.
 *
 * The interface exists so that shared Git code can depend on the capability instead of on the
 * concrete [CustomOnDiskCache]. That keeps callers testable without a filesystem or a Git host, and
 * lets the cache implementation be replaced without touching them.
 *
 * Implementations must be safe to call concurrently with the same coordinates: two callers asking
 * for one repository at the same time must leave exactly one clone behind.
 */
interface GitRepositoryCache {
    /**
     * Returns a path to a local clone of [coordinates], cloning it first if necessary.
     *
     * @param coordinates The repository to materialize locally, including the credentials to read it.
     * @return Absolute path to a local clone of the repository.
     * @throws RuntimeException if the repository cannot be cloned, for example because the stored
     *         credential no longer reaches it.
     */
    suspend fun getLocalRepositoryPath(coordinates: GitRepositoryCoordinates): Path
}
