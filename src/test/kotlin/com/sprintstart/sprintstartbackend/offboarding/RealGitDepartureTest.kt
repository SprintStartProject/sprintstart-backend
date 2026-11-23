package com.sprintstart.sprintstartbackend.offboarding

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileContribution
import com.sprintstart.sprintstartbackend.offboarding.scoring.DepartureQuery
import com.sprintstart.sprintstartbackend.offboarding.scoring.FileHistories
import com.sprintstart.sprintstartbackend.offboarding.scoring.PersonKey
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskLevel
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskMap
import com.sprintstart.sprintstartbackend.offboarding.scoring.RiskScorer
import com.sprintstart.sprintstartbackend.shared.git.CommitFiles
import com.sprintstart.sprintstartbackend.shared.git.DefaultGitOperationRunner
import com.sprintstart.sprintstartbackend.shared.git.GitConfig
import com.sprintstart.sprintstartbackend.shared.git.GitLog
import com.sprintstart.sprintstartbackend.shared.git.OnDiskOperations
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.time.Instant

/**
 * Runs real Git history through the whole chain: the log reader, the history builder with its rename
 * following, and the scorer. A mocked log would only prove how this code reads its own fixtures.
 */
class RealGitDepartureTest {
    @TempDir
    lateinit var repo: Path

    private val asOf = Instant.parse("2026-11-20T12:00:00Z")
    private var sequence = 0

    @BeforeEach
    fun initRepository() {
        git("init", "--quiet")
        git("config", "user.email", "committer@example.com")
        git("config", "user.name", "Committer")
        git("config", "commit.gpgsign", "false")
        git("config", "core.autocrlf", "false")
    }

    private fun git(vararg args: String, environment: Map<String, String> = emptyMap()): String {
        val process = ProcessBuilder(listOf("git") + args)
        process.environment().putAll(environment)
        return OnDiskOperations.exec(repo, process)
    }

    private fun write(name: String, content: String) {
        val target = repo.resolve(name)
        target.parent?.toFile()?.mkdirs()
        target.toFile().writeText(content)
    }

    private fun commit(author: String, date: String, message: String) {
        val moment = "${date}T10:00:00+00:00"
        git("add", "-A")
        git(
            "commit",
            "-m",
            message,
            "--quiet",
            "--author",
            "$author <$author@example.com>",
            environment = mapOf("GIT_AUTHOR_DATE" to moment, "GIT_COMMITTER_DATE" to moment),
        )
    }

    private fun edit(file: String, author: String, date: String) {
        write(file, existing(file) + "change ${sequence++}\n")
        commit(author, date, "edit $file")
    }

    private fun existing(file: String): String =
        repo
            .resolve(file)
            .toFile()
            .takeIf { it.exists() }
            ?.readText() ?: "line 1\nline 2\nline 3\nline 4\nline 5\n"

    private suspend fun contributions(): List<FileContribution> =
        GitLog(OnDiskOperations(), DefaultGitOperationRunner(), GitConfig())
            .commits(repo, sinceRevision = null)
            .flatMap { commit ->
                (commit.files as? CommitFiles.Listed)?.files.orEmpty().map {
                    FileContribution("git:local", it.path, it.renamedFrom, commit.authorEmail, commit.committedAt)
                }
            }

    private suspend fun leaving(vararg people: String): RiskMap {
        val query = DepartureQuery(people.map(PersonKey::parse).toSet(), asOf)
        return RiskScorer().score(FileHistories.build(contributions()), query)
    }

    private fun buildProject() {
        edit("engine/Core.kt", "ada", "2026-01-05")
        edit("engine/Core.kt", "ada", "2026-02-01")
        edit("engine/Core.kt", "ada", "2026-03-01")

        edit("docs/old/Guide.md", "ada", "2026-01-10")
        git("mv", "docs/old/Guide.md", "docs/Guide.md")
        write("docs/Guide.md", existing("docs/Guide.md") + "moved\n")
        commit("ada", "2026-02-10", "move the guide")

        edit("shared/Api.kt", "ada", "2026-09-20")
        listOf("21", "22", "23", "24").forEach { edit("shared/Api.kt", "ada", "2026-09-$it") }
        listOf("20", "21", "22", "23", "24").forEach { edit("shared/Api.kt", "ben", "2026-10-$it") }

        edit("ben/Notes.kt", "ben", "2026-10-01")
        edit("ben/Notes.kt", "ben", "2026-10-02")
    }

    @Test
    fun `a file only the leaving person ever changed is orphaned`() = runTest {
        buildProject()

        val risk = leaving("ada@example.com").files.single { it.path == "engine/Core.kt" }

        assertThat(risk.level).isEqualTo(RiskLevel.ORPHANED)
    }

    @Test
    fun `a file the leaving person moved is ranked under its new path only`() = runTest {
        buildProject()

        val paths = leaving("ada@example.com").files.map { it.path }

        assertThat(paths).contains("docs/Guide.md").doesNotContain("docs/old/Guide.md")
    }

    @Test
    fun `a file two people maintain is hoarded and names the one who stays`() = runTest {
        buildProject()

        val risk = leaving("ada@example.com").files.single { it.path == "shared/Api.kt" }

        assertThat(risk.level).isEqualTo(RiskLevel.HOARDED)
        assertThat(risk.remainingKnowers).containsExactly(PersonKey("ben@example.com"))
    }

    @Test
    fun `a file the leaving person never changed is left out and the summary counts every file`() = runTest {
        buildProject()

        val map = leaving("ada@example.com")

        assertThat(map.files.map { it.path }).doesNotContain("ben/Notes.kt")
        assertThat(map.summary.files).isEqualTo(4)
        assertThat(map.unknownDeparting).isEmpty()
    }

    @Test
    fun `both people leaving orphans the shared file`() = runTest {
        buildProject()

        val risk = leaving("ada@example.com", "ben@example.com").files.single { it.path == "shared/Api.kt" }

        assertThat(risk.level).isEqualTo(RiskLevel.ORPHANED)
    }
}
