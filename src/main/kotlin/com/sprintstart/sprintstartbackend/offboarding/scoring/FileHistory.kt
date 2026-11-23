package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import java.time.Instant

/** One person changing a file at one moment. */
data class Touch(
    val person: PersonKey,
    val at: Instant,
)

/**
 * Everything that happened to one file, oldest change first.
 *
 * @property repository The repository the file lives in.
 * @property path The file's latest path, after any renames.
 * @property touches The file's changes under every path it has had; never empty.
 */
class FileHistory(
    val repository: String,
    val path: String,
    val touches: List<Touch>,
) {
    /** The history as it stood at [asOf], or `null` when the file did not exist yet. */
    fun knownAt(asOf: Instant): FileHistory? {
        val seen = touches.filter { !it.at.isAfter(asOf) }
        return if (seen.isEmpty()) null else FileHistory(repository, path, seen)
    }
}

/** Builds file histories from the recorded changes. */
object FileHistories {
    /**
     * Groups changes into one history per file, following renames so a moved file keeps its past.
     *
     * Paths are only compared within a repository. A path that is reused after the file was deleted
     * cannot be told from the old file, because deletions are not recorded.
     */
    fun build(contributions: List<FileContribution>): List<FileHistory> =
        contributions.groupBy { it.repository }.flatMap { (repository, rows) ->
            val files = TrackedFiles()
            rows.sortedBy { it.changedAt }.forEach(files::record)
            files.histories(repository)
        }
}

private class TrackedFiles {
    private val idByPath = mutableMapOf<String, Int>()
    private val pathById = mutableMapOf<Int, String>()
    private val touchesById = mutableMapOf<Int, MutableList<Touch>>()
    private var nextId = 0

    fun record(row: FileContribution) {
        val id = idFor(row)
        idByPath[row.path] = id
        pathById[id] = row.path
        touchesById.getOrPut(id) { mutableListOf() }.add(Touch(PersonKey.ofEmail(row.authorEmail), row.changedAt))
    }

    fun histories(repository: String): List<FileHistory> =
        touchesById.map { (id, touches) -> FileHistory(repository, pathById.getValue(id), touches) }

    private fun idFor(row: FileContribution): Int =
        row.renamedFrom?.let { idByPath.remove(it) } ?: idByPath[row.path] ?: nextId++
}
