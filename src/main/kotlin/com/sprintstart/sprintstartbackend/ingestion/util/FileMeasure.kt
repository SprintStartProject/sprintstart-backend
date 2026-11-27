package com.sprintstart.sprintstartbackend.ingestion.util

import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind

/**
 * Measures how much a file holds, in the unit that fits its [FileKind].
 *
 * Code is measured by [IndentationComplexity], text by its words and settings by the lines that set
 * something, so a long page of documentation is not mistaken for flat, simple code.
 */
object FileMeasure {
    private val WORD = Regex("""[\p{L}\p{N}]+""")
    private val SOMETHING = Regex("""[\p{L}\p{N}]""")
    private val COMMENT = listOf("#", "//", ";", "<!--", "--", "*", "/*")

    /**
     * What was measured of one file.
     *
     * @property kind What the file holds.
     * @property complexity How much it holds, in the unit of its kind.
     * @property indentation The sum of the indentation levels of its non-blank lines, whatever its kind.
     * @property lines The number of its non-blank lines.
     */
    data class Measure(
        val kind: FileKind,
        val complexity: Double,
        val indentation: Double,
        val lines: Int,
    )

    /** Measures the [text] of the file at [path]. */
    fun of(path: String, text: String): Measure {
        val kind = FileKind.of(path)
        val indentation = IndentationComplexity.of(text)
        val complexity = when (kind) {
            FileKind.CODE -> indentation.indentation
            FileKind.PROSE -> WORD.findAll(text).count().toDouble()
            FileKind.CONFIG -> text.lineSequence().count(::isSetting).toDouble()
        }
        return Measure(kind, complexity, indentation.indentation, indentation.lines)
    }

    private fun isSetting(line: String): Boolean {
        val trimmed = line.trim()
        return SOMETHING.containsMatchIn(trimmed) && COMMENT.none { trimmed.startsWith(it) }
    }
}
