package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IdentitiesTest {
    private val ada = PersonKey("ada")
    private val adaEmail = PersonKey("ada@x.org")
    private val ben = PersonKey("ben")
    private val identities = Identities(listOf(AliasLink(adaEmail, ada)))

    @Test
    fun `an identity without a link counts as itself`() {
        assertThat(identities.resolve(ben)).isEqualTo(ben)
        assertThat(Identities().resolve(adaEmail)).isEqualTo(adaEmail)
    }

    @Test
    fun `a linked identity counts as the person it was linked to`() {
        assertThat(identities.resolve(adaEmail)).isEqualTo(ada)
        assertThat(identities.resolve(ada)).isEqualTo(ada)
    }

    @Test
    fun `two identities of one person leave a set as one person`() {
        assertThat(identities.resolve(setOf(adaEmail, ada, ben))).containsExactlyInAnyOrder(ada, ben)
    }

    @Test
    fun `a history credits every touch to the person it counts as`() {
        val file = FileHistory(
            REPOSITORY,
            "core.kt",
            listOf(touch("ada@x.org", 30), review("ada", 20), touch("ben", 10)),
        )

        val resolved = identities.resolve(file)

        assertThat(resolved.touches.map { it.person }).containsExactly(ada, ada, ben)
        assertThat(
            resolved.touches.map { it.source },
        ).containsExactly(SourceId.COMMITS, SourceId.REVIEWS, SourceId.COMMITS)
        assertThat(resolved.touches.map { it.at }).isEqualTo(file.touches.map { it.at })
        assertThat(resolved.path).isEqualTo("core.kt")
    }

    @Test
    fun `without links a history is returned as it is`() {
        val file = history("core.kt", "ada@x.org" to 10)

        assertThat(Identities().resolve(file)).isSameAs(file)
    }

    @Test
    fun `linking a developers identities changes who is left on a file`() {
        val file = history("core.kt", *repeated("ada@x.org", 3, 30), *repeated("ada", 3, 20))
        val departure = DepartureQuery(setOf(ada), NOW)

        val split = RiskScorer().score(listOf(file), departure)
        val folded = RiskScorer().score(listOf(identities.resolve(file)), departure)

        assertThat(split.files.single().remainingKnowers).containsExactly(adaEmail)
        assertThat(folded.files.single().remainingKnowers).isEmpty()
        assertThat(folded.files.single().level).isEqualTo(RiskLevel.ORPHANED)
    }
}
