package com.sprintstart.sprintstartbackend.shared.git

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

class GitLogParserTest {
    private val parser = GitLogParser(maxFilesPerCommit = 3)

    private fun header(
        sha: String = "abc",
        name: String = "Ada",
        email: String = "ada@example.com",
        date: String = "2026-01-02T03:04:05Z",
        subject: String = "Fix",
    ) = "\u001E" + listOf(sha, name, email, date, subject).joinToString("\u001F")

    private fun stat(added: String, removed: String, path: String) = "$added\t$removed\t$path\u0000"

    private fun rename(added: String, removed: String, from: String, to: String) =
        "$added\t$removed\t\u0000$from\u0000$to\u0000"

    /** A non-merge commit: header, newline, the file entries and the NUL Git adds after them. */
    private fun commit(header: String, vararg entries: String) = header + "\n" + entries.joinToString("") + "\u0000"

    private fun listed(commit: GitCommit) = (commit.files as CommitFiles.Listed).files

    @Test
    fun `reads the header fields of a commit`() {
        val output = commit(header(sha = "abc123", name = "Ada Lovelace", email = "ada@math.org", subject = "Fix it"))

        val parsed = parser.parse(output).single()

        assertThat(parsed.sha).isEqualTo("abc123")
        assertThat(parsed.authorName).isEqualTo("Ada Lovelace")
        assertThat(parsed.authorEmail).isEqualTo("ada@math.org")
        assertThat(parsed.subject).isEqualTo("Fix it")
        assertThat(parsed.committedAt).isEqualTo(Instant.parse("2026-01-02T03:04:05Z"))
    }

    @Test
    fun `keeps a subject that contains the unit separator or a printable delimiter`() {
        val output = commit(header(subject = "Revert \"Fix - the bug\"\u001Fand more"))

        assertThat(parser.parse(output).single().subject).isEqualTo("Revert \"Fix - the bug\"\u001Fand more")
    }

    @Test
    fun `accepts a commit time with a timezone offset`() {
        val output = commit(header(date = "2026-01-02T03:04:05+01:00"))

        assertThat(parser.parse(output).single().committedAt).isEqualTo(Instant.parse("2026-01-02T02:04:05Z"))
    }

    @Test
    fun `lists the files of a commit with their line counts`() {
        val output = commit(header(), stat("4", "3", "src/A.kt"), stat("0", "50", "src/B.kt"))

        assertThat(listed(parser.parse(output).single())).containsExactly(
            CommitFile("src/A.kt", linesAdded = 4, linesRemoved = 3, renamedFrom = null),
            CommitFile("src/B.kt", linesAdded = 0, linesRemoved = 50, renamedFrom = null),
        )
    }

    @Test
    fun `counts a binary file as zero lines but keeps the change`() {
        val output = commit(header(), stat("-", "-", "logo.png"))

        assertThat(listed(parser.parse(output).single()))
            .containsExactly(CommitFile("logo.png", 0, 0, renamedFrom = null))
    }

    @Test
    fun `keeps a path that contains spaces and a tab`() {
        val output = commit(header(), stat("1", "0", "dir with space/a\tb.txt"))

        assertThat(listed(parser.parse(output).single()).single().path).isEqualTo("dir with space/a\tb.txt")
    }

    @Test
    fun `reports a rename with its new and its old path`() {
        val output = commit(header(), rename("1", "0", "a.txt", "moved.txt"))

        assertThat(listed(parser.parse(output).single()))
            .containsExactly(CommitFile("moved.txt", 1, 0, renamedFrom = "a.txt"))
    }

    @Test
    fun `reads the files after a rename`() {
        val output = commit(header(), rename("1", "0", "a.txt", "b.txt"), stat("2", "1", "c.txt"))

        assertThat(listed(parser.parse(output).single()).map { it.path }).containsExactly("b.txt", "c.txt")
    }

    @Test
    fun `lists no files for a merge commit`() {
        val output = header(sha = "merge") + "\u0000"

        val parsed = parser.parse(output).single()

        assertThat(parsed.sha).isEqualTo("merge")
        assertThat(parsed.files).isEqualTo(CommitFiles.Listed(emptyList()))
    }

    @Test
    fun `lists no files for a commit at the end of the output that has none`() {
        assertThat(parser.parse(header()).single().files).isEqualTo(CommitFiles.Listed(emptyList()))
    }

    @Test
    fun `lists a commit that changes exactly the limit`() {
        val output = commit(header(), stat("1", "0", "a"), stat("1", "0", "b"), stat("1", "0", "c"))

        assertThat(parser.parse(output).single().files).isInstanceOf(CommitFiles.Listed::class.java)
    }

    @Test
    fun `reports a commit above the limit without listing its files`() {
        val output =
            commit(header(), stat("1", "0", "a"), stat("1", "0", "b"), stat("1", "0", "c"), stat("1", "0", "d"))

        assertThat(parser.parse(output).single().files).isEqualTo(CommitFiles.TooMany(4))
    }

    @Test
    fun `counts a rename as one file towards the limit`() {
        val output = commit(header(), rename("1", "0", "a", "b"), rename("1", "0", "c", "d"), stat("1", "0", "e"))

        assertThat(parser.parse(output).single().files).isInstanceOf(CommitFiles.Listed::class.java)
    }

    @Test
    fun `keeps each commit's files with that commit`() {
        val output = commit(header(sha = "first"), stat("1", "0", "one.kt")) +
            commit(header(sha = "second"), stat("2", "0", "two.kt"))

        val parsed = parser.parse(output)

        assertThat(parsed.map { it.sha }).containsExactly("first", "second")
        assertThat(listed(parsed[0]).map { it.path }).containsExactly("one.kt")
        assertThat(listed(parsed[1]).map { it.path }).containsExactly("two.kt")
    }

    @Test
    fun `ignores text before the first record`() {
        val output = "warning: something on the merged stream\n" + commit(header(sha = "first"))

        assertThat(parser.parse(output).map { it.sha }).containsExactly("first")
    }

    @Test
    fun `ignores an entry inside a file list that is not a file stat`() {
        val output = commit(header(), stat("1", "0", "a.kt"), "warning: stray\u0000", stat("2", "0", "b.kt"))

        assertThat(listed(parser.parse(output).single()).map { it.path }).containsExactly("a.kt", "b.kt")
    }

    @Test
    fun `ignores a rename whose names are cut off`() {
        val output = commit(header(), stat("1", "0", "a.kt")) + header(sha = "cut") + "\n1\t0\t\u0000only-one"

        val parsed = parser.parse(output)

        assertThat(parsed.map { it.sha }).containsExactly("abc", "cut")
        assertThat(parsed[1].files).isEqualTo(CommitFiles.Listed(emptyList()))
    }

    @Test
    fun `skips a record with too few header fields without losing the others`() {
        val output = "\u001Ebroken\u001Fonly-two\u0000" + commit(header(sha = "fine"))

        assertThat(parser.parse(output).map { it.sha }).containsExactly("fine")
    }

    @Test
    fun `skips a record whose timestamp cannot be parsed`() {
        val output = commit(header(sha = "broken", date = "not-a-date")) + commit(header(sha = "fine"))

        assertThat(parser.parse(output).map { it.sha }).containsExactly("fine")
    }

    @Test
    fun `skips a record with a blank sha`() {
        val output = commit(header(sha = "  ")) + commit(header(sha = "fine"))

        assertThat(parser.parse(output).map { it.sha }).containsExactly("fine")
    }

    @Test
    fun `returns nothing for empty output`() {
        assertThat(parser.parse("")).isEmpty()
    }
}
