package com.sprintstart.sprintstartbackend.upload.service

import org.springframework.stereotype.Component

/**
 * Extracts image paths from Markdown image syntax.
 *
 * The extractor returns the raw path inside each `![](...)` reference. Filename normalization is
 * handled by the linking service because it depends on the upload batch artifact map.
 */
@Component
class MarkdownImageReferenceExtractor {
    private val imageRegex =
        Regex("""!\[[^]]*]\((.*?)\)""")

    /**
     * Extracts raw targets from Markdown image references.
     *
     * The returned values are not normalized or validated here; callers decide how to interpret
     * relative paths, absolute paths, or unresolved references.
     *
     * @param markdown The markdown document content to scan.
     * @return Image targets in the order they appear in the document.
     */
    fun extract(markdown: String): List<String> {
        return imageRegex
            .findAll(markdown)
            .map { match ->
                match.groupValues[1]
            }.toList()
    }
}
