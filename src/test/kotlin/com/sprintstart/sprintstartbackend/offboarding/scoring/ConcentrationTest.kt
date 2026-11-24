package com.sprintstart.sprintstartbackend.offboarding.scoring

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class ConcentrationTest {
    private val ada = PersonKey("ada@x.org")
    private val ben = PersonKey("ben@x.org")
    private val carol = PersonKey("carol@x.org")

    @Test
    fun `nobody knowing the file is no knowers and no island`() {
        assertThat(Concentration.of(emptyMap())).isEqualTo(Concentration(0.0, null))
        assertThat(Concentration.of(mapOf(ada to 0.0))).isEqualTo(Concentration(0.0, null))
    }

    @Test
    fun `one person is one knower and an island`() {
        assertThat(Concentration.of(mapOf(ada to 5.0))).isEqualTo(Concentration(1.0, ada))
    }

    @Test
    fun `two equal people are two knowers`() {
        val concentration = Concentration.of(mapOf(ada to 3.0, ben to 3.0))

        assertThat(concentration.effectiveKnowers).isCloseTo(2.0, within(1e-9))
        assertThat(concentration.island).isNull()
    }

    @Test
    fun `a split of ninety-five to five is only a little more than one knower`() {
        val concentration = Concentration.of(mapOf(ada to 95.0, ben to 5.0))

        assertThat(concentration.effectiveKnowers).isCloseTo(1.1, within(0.01))
        assertThat(concentration.island).isEqualTo(ada)
    }

    @Test
    fun `just under ninety-five percent is no island`() {
        assertThat(Concentration.of(mapOf(ada to 94.0, ben to 6.0)).island).isNull()
    }

    @Test
    fun `three equal people are three knowers`() {
        val concentration = Concentration.of(mapOf(ada to 1.0, ben to 1.0, carol to 1.0))

        assertThat(concentration.effectiveKnowers).isCloseTo(3.0, within(1e-9))
    }
}
