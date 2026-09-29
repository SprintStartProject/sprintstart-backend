package com.sprintstart.sprintstartbackend.connectors.git.utils

/**
 * One file that could not be ingested, with the reason it was not.
 *
 * @property relativePath The repository-relative path of the file.
 * @property reason A human-readable explanation.
 */
data class GitIngestFailure(
    val relativePath: String,
    val reason: String,
)

/**
 * What an ingest did, so the caller can advance its own cursor.
 *
 * The engine deliberately does not persist [revision]: only the caller knows which of its own rows
 * tracks a repository's progress, and keeping that write outside the engine is what stops the
 * engine from depending on any connector's storage.
 *
 * @property revision The revision the repository ended up on. Callers store this as their cursor,
 *           which makes a repeated ingest of an unchanged repository a no-op.
 * @property failures Files that were part of [revision] but were not ingested.
 * @property resyncedPaths Every tracked path at [revision], present only when the engine fell back
 *           to a full ingest because the caller's cursor revision was missing from the clone. The
 *           caller reconciles deletions from it: stored files absent from the set were removed while
 *           the cursor could not see them. `null` on every incremental ingest.
 */
data class GitIngestOutcome(
    val revision: String,
    val failures: List<GitIngestFailure>,
    val resyncedPaths: Set<String>? = null,
) {
    /** Whether every file of the revision was ingested. */
    val complete: Boolean
        get() = failures.isEmpty()
}
