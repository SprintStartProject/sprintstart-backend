package com.sprintstart.sprintstartbackend.connectors.git.utils

/**
 * One file-level result of ingesting a repository revision.
 *
 * The change carries enough to be turned into a source-specific event without the engine knowing
 * anything about that source: the path identifies the artifact, [revision] lets the caller build a
 * browser URL in its own shape, and the caller supplies the hash unchanged rather than recomputing
 * it from [Modified.content].
 */
sealed interface GitFileChange {
    /** Path of the file relative to the repository root, always `/`-separated. */
    val relativePath: String

    /** Revision whose tree this change belongs to. */
    val revision: String

    /**
     * A file that is part of [revision] and was read as text.
     *
     * @property content The file's text content.
     * @property sha256 SHA-256 of the same bytes [content] was decoded from, so callers can use it
     *           for change detection without hashing the content a second time.
     */
    data class Modified(
        override val relativePath: String,
        override val revision: String,
        val content: String,
        val sha256: String,
    ) : GitFileChange

    /** A file that is no longer part of [revision] and must be removed from the index. */
    data class Deleted(
        override val relativePath: String,
        override val revision: String,
    ) : GitFileChange
}
