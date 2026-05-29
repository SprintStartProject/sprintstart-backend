package com.sprintstart.sprintstartbackend.upload.service

import org.springframework.stereotype.Component

@Component
class MarkdownImageReferenceExtractor {

    private val imageRegex =
        Regex("""!\[[^]]*]\((.*?)\)""")

    fun extract(markdown: String): List<String> {

        return imageRegex
            .findAll(markdown)
            .map { match ->
                match.groupValues[1]
            }
            .toList()
    }
}