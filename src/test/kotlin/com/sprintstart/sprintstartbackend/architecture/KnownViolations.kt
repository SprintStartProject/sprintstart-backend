package com.sprintstart.sprintstartbackend.architecture

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File

/**
 * A list of rule violations that existed when an architecture rule was introduced, which may only shrink.
 *
 * Lives in `src/test/resources/architecture/known-<rule>.txt`, one violation per line, `#` for comments.
 * [assertMatches] fails on a violation that is not listed (new code broke the rule) and on a listed one
 * that no longer occurs (it was fixed, so the entry must go). The current state is always written to
 * `build/architecture/<rule>.txt`; after a fix, copy it over the list and check the diff only removes lines.
 * The quality ratchet fails a PR whose list has more entries than its base branch.
 */
internal class KnownViolations(
    private val rule: String,
) {
    private val listFile = File("src/test/resources/architecture/known-$rule.txt")

    fun assertMatches(actual: Collection<String>, advice: String) {
        val current = actual.toSortedSet()
        writeCurrentState(current)
        val known = listFile
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .toSet()
        val new = current - known
        val fixed = known - current
        assertAll(
            { assertTrue(new.isEmpty()) { "New '$rule' violations — $advice\n" + lines(new) } },
            { assertTrue(fixed.isEmpty()) { "Fixed '$rule' violations, remove them from $listFile\n${lines(fixed)}" } },
        )
    }

    private fun lines(entries: Set<String>): String = entries.joinToString("\n")

    private fun writeCurrentState(current: Set<String>) {
        File("build/architecture/$rule.txt")
            .apply { parentFile.mkdirs() }
            .writeText(current.joinToString("\n", postfix = "\n"))
    }
}
