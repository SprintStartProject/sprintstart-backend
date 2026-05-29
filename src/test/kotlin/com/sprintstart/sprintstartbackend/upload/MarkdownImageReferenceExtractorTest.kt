package com.sprintstart.sprintstartbackend.upload

import com.sprintstart.sprintstartbackend.upload.service.MarkdownImageReferenceExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownImageReferenceExtractorTest {
    private val extractor =
        MarkdownImageReferenceExtractor()

    @Test
    fun `should extract image references`() {
        val markdown =
            """
            # Title
            
            ![](./img/logo.png)
            
            ![Diagram](../assets/test.webp)
            """.trimIndent()

        val result = extractor.extract(markdown)

        assertEquals(
            listOf(
                "./img/logo.png",
                "../assets/test.webp",
            ),
            result,
        )
    }

    @Test
    fun `should return empty list when no images`() {
        val markdown =
            """
            # Title
            
            Some text
            
            ## Subtitle
            """.trimIndent()

        val result = extractor.extract(markdown)

        assertEquals(emptyList<String>(), result)
    }
}
