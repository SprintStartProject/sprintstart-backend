package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FileHistoriesTest {
    private fun pathsOf(histories: List<FileHistory>) = histories.map { it.path }.sorted()

    @Test
    fun `changes of one path form one history, oldest first`() {
        val histories = FileHistories.build(
            listOf(
                contribution("a.kt", "ben@x.org", day("2026-03-01")),
                contribution("a.kt", "ada@x.org", day("2026-01-01")),
            ),
        )

        val only = histories.single()

        assertThat(only.touches.map { it.person.value }).containsExactly("ada@x.org", "ben@x.org")
    }

    @Test
    fun `the same path in two repositories is two files`() {
        val histories = FileHistories.build(
            listOf(
                contribution("a.kt", "ada@x.org", day("2026-01-01"), repository = "github:acme/one"),
                contribution("a.kt", "ada@x.org", day("2026-01-02"), repository = "github:acme/two"),
            ),
        )

        assertThat(histories.map { it.repository }).containsExactlyInAnyOrder("github:acme/one", "github:acme/two")
    }

    @Test
    fun `a renamed file keeps its history under the new path`() {
        val histories = FileHistories.build(
            listOf(
                contribution("old/A.kt", "ada@x.org", day("2026-01-01")),
                contribution("new/A.kt", "ben@x.org", day("2026-02-01"), renamedFrom = "old/A.kt"),
                contribution("new/A.kt", "ada@x.org", day("2026-03-01")),
            ),
        )

        val moved = histories.single()

        assertThat(moved.path).isEqualTo("new/A.kt")
        assertThat(moved.touches).hasSize(3)
    }

    @Test
    fun `a file renamed twice is still one file`() {
        val histories = FileHistories.build(
            listOf(
                contribution("a.kt", "ada@x.org", day("2026-01-01")),
                contribution("b.kt", "ada@x.org", day("2026-02-01"), renamedFrom = "a.kt"),
                contribution("c.kt", "ada@x.org", day("2026-03-01"), renamedFrom = "b.kt"),
            ),
        )

        assertThat(pathsOf(histories)).containsExactly("c.kt")
    }

    @Test
    fun `a file renamed back to its first path is still one file`() {
        val histories = FileHistories.build(
            listOf(
                contribution("a.kt", "ada@x.org", day("2026-01-01")),
                contribution("b.kt", "ada@x.org", day("2026-02-01"), renamedFrom = "a.kt"),
                contribution("a.kt", "ada@x.org", day("2026-03-01"), renamedFrom = "b.kt"),
            ),
        )

        assertThat(histories.single().touches).hasSize(3)
        assertThat(histories.single().path).isEqualTo("a.kt")
    }

    @Test
    fun `a rename from a path nobody recorded starts a history at the new path`() {
        val histories = FileHistories.build(
            listOf(contribution("new.kt", "ada@x.org", day("2026-02-01"), renamedFrom = "never-seen.kt")),
        )

        assertThat(pathsOf(histories)).containsExactly("new.kt")
    }

    @Test
    fun `a rename in one repository does not move a file of the same name in another`() {
        val histories = FileHistories.build(
            listOf(
                contribution("a.kt", "ada@x.org", day("2026-01-01"), repository = "github:acme/one"),
                contribution("a.kt", "ben@x.org", day("2026-01-02"), repository = "github:acme/two"),
                contribution(
                    "b.kt",
                    "ada@x.org",
                    day("2026-02-01"),
                    renamedFrom = "a.kt",
                    repository = "github:acme/one",
                ),
            ),
        )

        assertThat(histories.map { it.repository to it.path })
            .containsExactlyInAnyOrder("github:acme/one" to "b.kt", "github:acme/two" to "a.kt")
    }

    @Test
    fun `a history as of an earlier date leaves out later changes`() {
        val file = history("a.kt", "ada@x.org" to 100, "ben@x.org" to 10)

        val earlier = file.knownAt(daysAgo(50))

        assertThat(earlier?.touches?.map { it.person.value }).containsExactly("ada@x.org")
    }

    @Test
    fun `a file that did not exist yet has no history as of an earlier date`() {
        assertThat(history("a.kt", "ada@x.org" to 10).knownAt(daysAgo(50))).isNull()
    }

    @Test
    fun `a change made exactly at the date is known`() {
        assertThat(history("a.kt", "ada@x.org" to 10).knownAt(daysAgo(10))).isNotNull()
    }
}
