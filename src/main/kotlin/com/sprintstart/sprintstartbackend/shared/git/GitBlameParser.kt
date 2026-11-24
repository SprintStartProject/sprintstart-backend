package com.sprintstart.sprintstartbackend.shared.git

/**
 * Reads the output of `git blame --line-porcelain`.
 *
 * In that format every line of the file is described by a block of header lines, one of which is
 * `author-mail <address>`, followed by the text of the line behind a tab. Only the header lines start
 * with a letter, so counting them cannot be confused by the content of the file.
 */
object GitBlameParser {
    private const val AUTHOR_MAIL = "author-mail "

    /**
     * Counts how many lines each author wrote.
     *
     * @param output What `git blame --line-porcelain` printed.
     * @return The number of lines by author email, lower-cased and without the angle brackets; lines
     *         whose author is not known are left out.
     */
    fun parse(output: String): Map<String, Int> =
        output
            .lineSequence()
            .filter { it.startsWith(AUTHOR_MAIL) }
            .map {
                it
                    .removePrefix(AUTHOR_MAIL)
                    .trim()
                    .removeSurrounding("<", ">")
                    .lowercase()
            }.filter { it.isNotBlank() }
            .groupingBy { it }
            .eachCount()
}
