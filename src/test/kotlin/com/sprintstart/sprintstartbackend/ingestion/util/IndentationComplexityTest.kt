package com.sprintstart.sprintstartbackend.ingestion.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class IndentationComplexityTest {
    @Test
    fun `a flat file has no indentation`() {
        assertThat(IndentationComplexity.of("a\nb\nc")).isEqualTo(IndentationComplexity.Measure(0.0, 3))
    }

    @Test
    fun `every level of four columns counts one`() {
        val text = "fun a() {\n    if (x) {\n        y()\n    }\n}"

        assertThat(IndentationComplexity.of(text)).isEqualTo(IndentationComplexity.Measure(1 + 2 + 1.0, 5))
    }

    @Test
    fun `a tab counts as a whole level`() {
        assertThat(IndentationComplexity.of("a\n\tb\n\t\tc").indentation).isEqualTo(3.0)
    }

    @Test
    fun `spaces and tabs add up and two spaces are half a level`() {
        assertThat(IndentationComplexity.of("  a\n \tb").indentation).isEqualTo(0.5 + 1.25)
    }

    @Test
    fun `blank lines are not counted at all`() {
        val measure = IndentationComplexity.of("a\n\n        \n    b\n")

        assertThat(measure).isEqualTo(IndentationComplexity.Measure(1.0, 2))
    }

    @Test
    fun `an empty file is nothing`() {
        assertThat(IndentationComplexity.of("")).isEqualTo(IndentationComplexity.Measure(0.0, 0))
    }

    @Test
    fun `windows line endings are read like unix ones`() {
        assertThat(IndentationComplexity.of("a\r\n    b\r\n")).isEqualTo(IndentationComplexity.Measure(1.0, 2))
    }
}
