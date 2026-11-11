package com.sprintstart.sprintstartbackend.shared.git

/**
 * The files a commit changed.
 *
 * A commit that touches an enormous number of files is almost always a reformat, a vendor update
 * or a generated change, and listing its files would drown the real signal. Such a commit is
 * reported as [TooMany] instead, so a consumer can tell "changed nothing we can list" apart from
 * "changed too much to list".
 */
sealed interface CommitFiles {
    /**
     * The complete list of changed files.
     *
     * A merge commit is listed with no files, because the commits it merges already carry the same
     * changes and counting both would double them.
     *
     * @property files The changed files, in the order Git reported them.
     */
    data class Listed(
        val files: List<CommitFile>,
    ) : CommitFiles

    /**
     * A commit that changed more files than the configured limit, so none are listed.
     *
     * @property count How many files the commit changed.
     */
    data class TooMany(
        val count: Int,
    ) : CommitFiles
}

/**
 * One file changed by a commit.
 *
 * A binary file reports no line counts in Git, so both counts are zero for it. The change itself is
 * still real and is kept.
 *
 * @property path The file's path after the commit.
 * @property linesAdded Lines added to the file.
 * @property linesRemoved Lines removed from the file.
 * @property renamedFrom The path the file had before the commit when the commit moved it, so a
 *           consumer can keep a moved file's history attached to its new path. `null` when the
 *           file kept its path.
 */
data class CommitFile(
    val path: String,
    val linesAdded: Int,
    val linesRemoved: Int,
    val renamedFrom: String?,
)
