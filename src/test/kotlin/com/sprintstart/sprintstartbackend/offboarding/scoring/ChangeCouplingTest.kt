package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import java.util.UUID

class ChangeCouplingTest {
    private val commits = List(10) { UUID.randomUUID() }

    private fun file(path: String, vararg changedIn: Int, repository: String = REPOSITORY) =
        FileHistory(
            repository,
            path,
            changedIn.mapIndexed { n, commit ->
                Touch(PersonKey("ada@x.org"), daysAgo(100L - n), commit = commits[commit])
            },
        )

    private fun coupling(vararg files: FileHistory, minShared: Int = 3, minDegree: Double = 0.3) =
        ChangeCoupling(files.toList(), minShared, minDegree)

    @Test
    fun `files that always change together are coupled completely`() {
        val a = file("a.kt", 0, 1, 2)
        val b = file("b.kt", 0, 1, 2)

        val ofA = coupling(a, b).of(a)

        assertThat(ofA.partners.single().path).isEqualTo("b.kt")
        assertThat(ofA.partners.single().degree).isCloseTo(1.0, within(1e-9))
        assertThat(ofA.sum).isCloseTo(1.0, within(1e-9))
    }

    @Test
    fun `the degree is the shared commits over the average of the two files commits`() {
        val a = file("a.kt", 0, 1, 2, 3)
        val b = file("b.kt", 0, 1, 2, 4, 5, 6)

        assertThat(
            coupling(a, b)
                .of(a)
                .partners
                .single()
                .degree,
        ).isCloseTo(3 / 5.0, within(1e-9))
    }

    @Test
    fun `a pair that changed together too rarely is not coupled`() {
        val a = file("a.kt", 0, 1)
        val b = file("b.kt", 0, 1)

        assertThat(coupling(a, b).of(a)).isEqualTo(Coupling.NONE)
    }

    @Test
    fun `a pair whose degree is too low is not coupled`() {
        val a = file("a.kt", 0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
        val b = file("b.kt", 0, 1, 2)

        assertThat(coupling(a, b).of(b).sum).isCloseTo(3 / 6.5, within(1e-9))
        assertThat(coupling(a, b, minDegree = 0.5).of(b)).isEqualTo(Coupling.NONE)
    }

    @Test
    fun `the limits can be changed`() {
        val a = file("a.kt", 0, 1)
        val b = file("b.kt", 0, 1)

        assertThat(coupling(a, b, minShared = 2).of(a).partners).hasSize(1)
    }

    @Test
    fun `a file lists its three strongest partners and sums all of them`() {
        val a = file("a.kt", 0, 1, 2)
        val partners =
            listOf(file("b.kt", 0, 1, 2), file("c.kt", 0, 1, 2), file("d.kt", 0, 1, 2), file("e.kt", 0, 1, 2))

        val ofA = coupling(a, *partners.toTypedArray()).of(a)

        assertThat(ofA.partners.map { it.path }).containsExactly("b.kt", "c.kt", "d.kt")
        assertThat(ofA.sum).isCloseTo(4.0, within(1e-9))
    }

    @Test
    fun `files in different repositories are different files`() {
        val a = file("a.kt", 0, 1, 2, repository = "github:acme/one")
        val same = file("a.kt", 0, 1, 2, repository = "github:acme/two")

        val ofA = coupling(a, same).of(a)

        assertThat(ofA.partners.single().repository).isEqualTo("github:acme/two")
    }

    @Test
    fun `changes without a commit tie nothing together`() {
        val a = history("a.kt", "ada@x.org" to 3, "ada@x.org" to 2, "ada@x.org" to 1)
        val b = history("b.kt", "ada@x.org" to 3, "ada@x.org" to 2, "ada@x.org" to 1)

        assertThat(coupling(a, b).of(a)).isEqualTo(Coupling.NONE)
    }

    @Test
    fun `a ranking tells each file what it changes together with`() {
        val a = file("a.kt", 0, 1, 2)
        val b = file("b.kt", 0, 1, 2)
        val query = DepartureQuery(setOf(PersonKey("ada@x.org")), NOW)

        val risks = RiskScorer().score(listOf(a, b), query).files

        assertThat(
            risks
                .first { it.path == "a.kt" }
                .coupling.partners
                .map { it.path },
        ).containsExactly("b.kt")
        assertThat(risks.first { it.path == "a.kt" }.reasons).contains("It usually changes together with b.kt.")
    }

    @Test
    fun `a file that is renamed keeps the commits it was coupled through`() {
        val commit = commits[0]
        val rows = listOf(
            contribution("old/A.kt", "ada@x.org", daysAgo(30), commit = commit),
            contribution("new/A.kt", "ada@x.org", daysAgo(20), renamedFrom = "old/A.kt", commit = commits[1]),
        )

        val file = FileHistories.build(rows).single()

        assertThat(file.touches.map { it.commit }).containsExactly(commit, commits[1])
    }

    @Test
    fun `a file that changes alone has no coupling`() {
        assertThat(coupling(file("a.kt", 0, 1, 2)).of(file("a.kt", 0, 1, 2))).isEqualTo(Coupling.NONE)
    }
}
