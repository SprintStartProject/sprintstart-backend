package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution

/**
 * Drops the changes that say nothing about who knows a file, before any knowledge is credited.
 *
 * A wrong credit weighs most in a small team, where one person wrongly credited can be half of it.
 * Dropped are changes by bots, changes to files nobody reads as code (lockfiles, vendored and
 * generated files, binaries), and every file of a commit that touched [MASS_COMMIT_FILES] or more
 * files, which is a reformat or a move, not knowledge.
 *
 * A mass commit that moves files is dropped with its renames, so those files start a new history at
 * their new path.
 */
object Preprocessing {
    /** A commit that changes this many files, after the ignored ones are left out, credits nobody. */
    const val MASS_COMMIT_FILES = 100

    private val BOT_NAME = Regex("""\[bot]|^(dependabot|renovate|github-actions)(@|$)""", RegexOption.IGNORE_CASE)
    private val IGNORED_DIRECTORY = Regex("""(^|/)(vendor|node_modules|dist|build|generated|__generated__|\.gradle)/""")
    private val IGNORED_NAME = Regex(
        listOf(
            """(^|/)(package-lock\.json|yarn\.lock|pnpm-lock\.yaml|Cargo\.lock|poetry\.lock)$""",
            """(^|/)(Gemfile\.lock|composer\.lock|go\.sum|gradle\.lockfile)$""",
            """\.min\.(js|css)$""",
            """\.(png|jpe?g|gif|ico|pdf|zip|jar|woff2?|ttf|eot|class)$""",
        ).joinToString("|"),
        RegexOption.IGNORE_CASE,
    )

    /** The changes that count, in their original order. */
    fun clean(rows: List<FileContribution>, massCommitFiles: Int = MASS_COMMIT_FILES): List<FileContribution> {
        val kept = rows.filterNot { isBot(it.authorEmail) || isIgnored(it.path) }
        val sizes = kept.mapNotNull { it.commit }.groupingBy { it }.eachCount()
        return kept.filter { row -> row.commit == null || sizes.getValue(row.commit) < massCommitFiles }
    }

    /** Whether [email] belongs to an automation, not a person. */
    fun isBot(email: String): Boolean = BOT_NAME.containsMatchIn(email)

    /** Whether [path] is a file whose changes are not read as knowledge. */
    fun isIgnored(path: String): Boolean = IGNORED_DIRECTORY.containsMatchIn(path) || IGNORED_NAME.containsMatchIn(path)
}
