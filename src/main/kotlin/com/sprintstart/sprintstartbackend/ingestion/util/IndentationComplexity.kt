package com.sprintstart.sprintstartbackend.ingestion.util

/**
 * Estimates how complicated a source file is from how deeply its lines are indented, so no parser
 * and no knowledge of the language is needed.
 *
 * Deeply nested code is where the branches are, so the indentation of every non-blank line, counted in
 * levels of [INDENT_UNIT] columns with a tab as one level, is summed up. It is a stand-in for the
 * cyclomatic complexity that works for any language, not a measure of it.
 */
object IndentationComplexity {
    /** The columns of one level of indentation. */
    const val INDENT_UNIT = 4

    /**
     * What was measured of one file.
     *
     * @property indentation The sum of the indentation levels of its non-blank lines.
     * @property lines The number of its non-blank lines.
     */
    data class Measure(
        val indentation: Double,
        val lines: Int,
    )

    /** Measures the text of one file. */
    fun of(text: String): Measure {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        return Measure(lines.sumOf(::levelsOf), lines.size)
    }

    private fun levelsOf(line: String): Double {
        val columns = line.takeWhile { it == ' ' || it == '\t' }.sumOf { if (it == '\t') INDENT_UNIT else 1 }
        return columns.toDouble() / INDENT_UNIT
    }
}
