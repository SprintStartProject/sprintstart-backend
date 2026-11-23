package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

class SuccessorFinderTest {
    private val commits = List(10) { UUID.randomUUID() }
    private val ada = PersonKey("ada@x.org")
    private val ben = PersonKey("ben@x.org")
    private val carol = PersonKey("carol@x.org")
    private val dan = PersonKey("dan@x.org")

    private fun file(path: String, vararg changes: Pair<PersonKey, Int>) =
        FileHistory(
            REPOSITORY,
            path,
            changes.mapIndexed { n, (person, commit) -> Touch(person, daysAgo(100L - n), commit = commits[commit]) },
        )

    /** `core.kt` changes with `util.kt` in commits 0, 1 and 2; Ben also changed util.kt twice more. */
    private val core = file("core.kt", ada to 0, ada to 1, ada to 2)
    private val util = file(
        "util.kt",
        ada to 0,
        ben to 0,
        carol to 1,
        carol to 1,
        ben to 2,
        dan to 2,
        ben to 5,
        ben to 6,
    )
    private val all = listOf(core, util)

    private fun finder(excluded: Set<PersonKey> = setOf(ada)) =
        SuccessorFinder(all, ChangeCoupling(all), excluded)

    @Test
    fun `the people who changed the coupled files are suggested, best first`() {
        val suggested = finder().suggest(core, knowers = emptySet())

        assertThat(suggested.map { it.person }).containsExactly(ben, carol, dan)
        assertThat(suggested.first().score).isEqualTo(4 * 3.0)
    }

    @Test
    fun `people who left or are inactive are never suggested`() {
        val suggested = finder(excluded = setOf(ada, ben)).suggest(core, knowers = emptySet())

        assertThat(suggested.map { it.person }).containsExactly(carol, dan)
    }

    @Test
    fun `people who already know the file are not suggested`() {
        assertThat(finder().suggest(core, knowers = setOf(ben)).map { it.person }).containsExactly(carol, dan)
    }

    @Test
    fun `at most three people are suggested`() {
        val many = (1..5).map { PersonKey("p$it@x.org") }
        val busy =
            file(
                "busy.kt",
                *many.map { it to 0 }.toTypedArray(),
                *many.map { it to 1 }.toTypedArray(),
                *many
                    .map {
                        it to
                            2
                    }.toTypedArray(),
            )
        val files = listOf(core, busy)

        assertThat(SuccessorFinder(files, ChangeCoupling(files), setOf(ada)).suggest(core, emptySet())).hasSize(3)
    }

    @Test
    fun `a file that changes alone has nobody to suggest`() {
        val alone = file("alone.kt", ada to 7, ada to 8, ada to 9)
        val files = listOf(alone, util)

        assertThat(SuccessorFinder(files, ChangeCoupling(files), setOf(ada)).suggest(alone, emptySet())).isEmpty()
    }

    @Test
    fun `a ranking carries the successors of every file at risk`() {
        val map = RiskScorer().score(all, DepartureQuery(setOf(ada), NOW))

        val coreRisk = map.files.first { it.path == "core.kt" }
        assertThat(coreRisk.successors.map { it.person }).containsExactly(ben, carol, dan)
    }
}
