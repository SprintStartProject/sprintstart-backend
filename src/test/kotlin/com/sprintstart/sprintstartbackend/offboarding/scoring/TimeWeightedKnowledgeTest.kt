package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TimeWeightedKnowledgeTest {
    private val timeWeighted = WeightProfile(knowledge = KnowledgeSettings(KnowledgeModel.TIME_WEIGHTED))

    private fun knowers(file: FileHistory, profile: WeightProfile = timeWeighted) =
        TimeWeightedKnowledge(profile, NOW).authorsOf(file).map { it.value }

    @Test
    fun `a recent commit is worth one and a fresh creation four`() {
        val file = history("a.kt", "ada@x.org" to 0, "ben@x.org" to 0)

        val knowledge = TimeWeightedKnowledge(timeWeighted, NOW).knowledgeOf(file)

        assertThat(knowledge.getValue(PersonKey("ada@x.org"))).isCloseTo(4.0, within(1e-9))
        assertThat(knowledge.getValue(PersonKey("ben@x.org"))).isCloseTo(1.0, within(1e-9))
    }

    @Test
    fun `a commit fades by half every half-life`() {
        val file = history("a.kt", "ada@x.org" to 400, "ben@x.org" to 152)

        val ben = TimeWeightedKnowledge(timeWeighted, NOW).knowledgeOf(file).getValue(PersonKey("ben@x.org"))

        assertThat(ben).isCloseTo(0.5, within(0.01))
    }

    @Test
    fun `whoever has gathered enough weight knows the file and nobody else does`() {
        val file = history("a.kt", "ada@x.org" to 400, "ben@x.org" to 0, "carol@x.org" to 300)

        assertThat(knowers(file)).containsExactly("ben@x.org")
    }

    @Test
    fun `a faded creation no longer counts as knowing the file`() {
        assertThat(knowers(history("a.kt", "ada@x.org" to 1000))).isEmpty()
    }

    @Test
    fun `reviews add half of a commit and cannot found a file`() {
        val withReviews = timeWeighted.copy(sourceWeights = mapOf(SourceId.COMMITS to 1.0, REVIEWS to 0.5))
        val file = reviewedHistory("a.kt", listOf("ada@x.org" to 400), listOf("ben@x.org" to 0, "ben@x.org" to 0))

        val knowledge = TimeWeightedKnowledge(withReviews, NOW).knowledgeOf(file)

        assertThat(knowledge.getValue(PersonKey("ben@x.org"))).isCloseTo(1.0, within(1e-9))
        assertThat(knowers(file, withReviews)).containsExactly("ben@x.org")
    }

    @Test
    fun `the threshold and the bonus can be changed`() {
        val file = history("a.kt", "ada@x.org" to 0, "ben@x.org" to 0)
        val strict = timeWeighted.copy(knowledge = KnowledgeSettings(KnowledgeModel.TIME_WEIGHTED, knowsAtLeast = 2.0))
        val noBonus = timeWeighted.copy(
            knowledge = KnowledgeSettings(KnowledgeModel.TIME_WEIGHTED, knowsAtLeast = 2.0, foundingBonus = 0.0),
        )

        assertThat(knowers(file, strict)).containsExactly("ada@x.org")
        assertThat(knowers(file, noBonus)).isEmpty()
    }

    @Test
    fun `the profile chooses the model`() {
        assertThat(Knowledge.of(WeightProfile(), NOW)).isInstanceOf(Authorship::class.java)
        assertThat(Knowledge.of(timeWeighted, NOW)).isInstanceOf(TimeWeightedKnowledge::class.java)
    }

    @Test
    fun `a ranking under this model puts a file whose only knower leaves on the map`() {
        val file = history("a.kt", "ada@x.org" to 400, "ben@x.org" to 0, "carol@x.org" to 0)
        val map = RiskScorer().score(
            listOf(file),
            DepartureQuery(setOf(PersonKey("ben@x.org")), NOW, timeWeighted),
        )

        assertThat(map.files.single().level).isEqualTo(RiskLevel.HOARDED)
        assertThat(map.files.single().remainingKnowers).containsExactly(PersonKey("carol@x.org"))
    }

    @Test
    fun `settings must be sensible`() {
        assertThrows<IllegalArgumentException> { KnowledgeSettings(knowsAtLeast = 0.0) }
        assertThrows<IllegalArgumentException> { KnowledgeSettings(foundingBonus = -1.0) }
    }
}
