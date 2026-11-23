package com.sprintstart.sprintstartbackend.shared.git

import java.time.Instant

/**
 * Parses the output of the `git log` command built by [OnDiskOperations.gitLog].
 *
 * The output is a sequence of records, each starting with the ASCII record separator. A record is
 * a header of unit-separated fields, then the commit's `--numstat` entries, each terminated by NUL.
 * A rename entry has an empty path and is followed by the old and the new path as two more
 * NUL-terminated names.
 *
 * Git writes warnings and progress notices to the same merged stream, so parsing never fails the
 * whole read: a record with a malformed header is skipped, and a stray line inside a file list is
 * ignored, so one bad line cannot cost the caller every commit after it.
 *
 * @property maxFilesPerCommit The most files a commit may change and still have them listed.
 */
internal class GitLogParser(
    private val maxFilesPerCommit: Int,
) {
    /**
     * Parses all records in [output].
     *
     * @return The commits, in the order Git printed them.
     */
    fun parse(output: String): List<GitCommit> =
        output
            .split(RECORD_SEPARATOR)
            .drop(1)
            .mapNotNull(::parseRecord)

    private fun parseRecord(record: String): GitCommit? {
        val headerEnd = record.indexOfFirst { it == '\n' || it == NUL }.takeIf { it >= 0 } ?: record.length
        val header = parseHeader(record.substring(0, headerEnd)) ?: return null
        val files = limitFiles(parseEntries(record.substring(headerEnd).trimStart('\n').split(NUL)))

        return GitCommit(
            sha = header.sha,
            authorName = header.authorName,
            authorEmail = header.authorEmail,
            committedAt = header.committedAt,
            subject = header.subject,
            files = files,
        )
    }

    private fun parseHeader(text: String): Header? {
        val fields = text.split(FIELD_SEPARATOR, limit = HEADER_FIELD_COUNT)
        if (fields.size < HEADER_FIELD_COUNT) return null

        val sha = fields[SHA].trim()
        if (sha.isEmpty()) return null
        val committedAt = runCatching { Instant.parse(fields[COMMITTED_AT].trim()) }.getOrNull() ?: return null

        return Header(sha, fields[AUTHOR_NAME], fields[AUTHOR_EMAIL], committedAt, fields[SUBJECT])
    }

    private fun parseEntries(tokens: List<String>): List<CommitFile> {
        val files = mutableListOf<CommitFile>()
        var index = 0
        while (index < tokens.size) {
            val entry = readEntry(tokens, index)
            if (entry == null) {
                index += 1
            } else {
                files += entry.file
                index += entry.tokensUsed
            }
        }
        return files
    }

    private fun readEntry(tokens: List<String>, index: Int): Entry? {
        val stat = STAT_PATTERN.matchEntire(tokens[index]) ?: return null
        val (added, removed, path) = stat.destructured
        if (path.isNotEmpty()) return Entry(CommitFile(path, count(added), count(removed), null), 1)

        val names = tokens.subList(index + 1, minOf(tokens.size, index + RENAME_TOKENS))
        if (names.size < RENAME_NAMES) return null
        return Entry(CommitFile(names[1], count(added), count(removed), names[0]), RENAME_TOKENS)
    }

    private fun limitFiles(files: List<CommitFile>): CommitFiles =
        if (files.size > maxFilesPerCommit) CommitFiles.TooMany(files.size) else CommitFiles.Listed(files)

    /** Git prints `-` instead of a number for a binary file. */
    private fun count(text: String): Int = text.toIntOrNull() ?: 0

    private data class Header(
        val sha: String,
        val authorName: String,
        val authorEmail: String,
        val committedAt: Instant,
        val subject: String,
    )

    private data class Entry(
        val file: CommitFile,
        val tokensUsed: Int,
    )

    private companion object {
        const val RECORD_SEPARATOR = '\u001E'
        const val FIELD_SEPARATOR = '\u001F'
        const val NUL = '\u0000'
        const val HEADER_FIELD_COUNT = 5
        const val SHA = 0
        const val AUTHOR_NAME = 1
        const val AUTHOR_EMAIL = 2
        const val COMMITTED_AT = 3
        const val SUBJECT = 4

        /** A rename entry is the stat token plus the old and the new name. */
        const val RENAME_TOKENS = 3
        const val RENAME_NAMES = 2

        /** `added<TAB>removed<TAB>path`, where the path may itself contain tabs or newlines. */
        val STAT_PATTERN = Regex("^(\\d+|-)\t(\\d+|-)\t(.*)$", RegexOption.DOT_MATCHES_ALL)
    }
}
