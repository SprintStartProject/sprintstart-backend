package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreprocessingTest {
    private val at = Instant.parse("2026-01-01T00:00:00Z")

    private fun row(path: String, email: String = "ada@x.org", commit: UUID? = null) =
        FileContribution("github:acme/app", path, null, email, at, commit)

    @Test
    fun `bots are not people`() {
        listOf(
            "49699333+dependabot[bot]@users.noreply.github.com",
            "renovate[bot]@users.noreply.github.com",
            "github-actions@github.com",
            "dependabot@github.com",
        ).forEach { assertTrue(Preprocessing.isBot(it), it) }
        listOf("ada@x.org", "robot.fan@x.org", "abbot@x.org").forEach { assertFalse(Preprocessing.isBot(it), it) }
    }

    @Test
    fun `lockfiles vendored and generated files and binaries are ignored`() {
        listOf(
            "package-lock.json",
            "web/yarn.lock",
            "go.sum",
            "vendor/lib/a.go",
            "web/node_modules/x/i.js",
            "app/build/out.txt",
            "src/generated/Api.kt",
            "assets/logo.PNG",
            "static/app.min.js",
            "lib/a.jar",
        ).forEach { assertTrue(Preprocessing.isIgnored(it), it) }
        listOf("src/Main.kt", "docs/builder.md", "src/vendors.kt", "README.md", "src/Lockfile.kt")
            .forEach { assertFalse(Preprocessing.isIgnored(it), it) }
    }

    @Test
    fun `changes by bots and to ignored files are dropped`() {
        val rows = listOf(
            row("src/A.kt"),
            row("src/A.kt", "dependabot[bot]@users.noreply.github.com"),
            row("package-lock.json"),
        )

        assertEquals(listOf(rows[0]), Preprocessing.clean(rows))
    }

    @Test
    fun `a commit of a hundred files or more credits nobody`() {
        val mass = UUID.randomUUID()
        val small = UUID.randomUUID()
        val rows =
            (1..100).map { row("src/F$it.kt", commit = mass) } + (1..99).map { row("lib/G$it.kt", commit = small) }

        val kept = Preprocessing.clean(rows)

        assertEquals(99, kept.size)
        assertTrue(kept.all { it.commit == small })
    }

    @Test
    fun `ignored files do not make a commit a mass commit`() {
        val commit = UUID.randomUUID()
        val rows =
            (1..60).map { row("src/F$it.kt", commit = commit) } + (1..60).map { row("vendor/V$it.kt", commit = commit) }

        assertEquals(60, Preprocessing.clean(rows).size)
    }

    @Test
    fun `the limit can be changed and a change of unknown commit is never a mass commit`() {
        val commit = UUID.randomUUID()
        val rows = (1..5).map { row("src/F$it.kt", commit = commit) } + row("src/U.kt")

        assertEquals(1, Preprocessing.clean(rows, massCommitFiles = 5).size)
        assertEquals(6, Preprocessing.clean(rows, massCommitFiles = 6).size)
    }
}
