package com.sprintstart.sprintstartbackend.upload

import com.sprintstart.sprintstartbackend.upload.service.MarkdownImageReferenceExtractor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownImageReferenceExtractorTest {
    private val extractor =
        MarkdownImageReferenceExtractor()

    @Test
    fun `extract returns empty list when markdown contains no images`() {
        val markdown =
            """
            # Title
            
            Some text here.
            """.trimIndent()

        val result =
            extractor.extract(markdown)

        assertEquals(
            emptyList<String>(),
            result,
        )
    }

    @Test
    fun `extract finds single image`() {
        val markdown =
            """
            ![Logo](logo.png)
            """.trimIndent()

        val result =
            extractor.extract(markdown)

        assertEquals(
            listOf("logo.png"),
            result,
        )
    }

    @Test
    fun `extract finds multiple images`() {
        val markdown =
            """
            ![One](one.png)
            Some text
            ![Two](two.jpg)
            ![Three](three.webp)
            """.trimIndent()

        val result =
            extractor.extract(markdown)

        assertEquals(
            listOf(
                "one.png",
                "two.jpg",
                "three.webp",
            ),
            result,
        )
    }

    @Test
    fun `extract preserves relative paths`() {
        val markdown =
            """
            ![Image](images/logo.png)
            ![Image](../assets/banner.jpg)
            """.trimIndent()

        val result =
            extractor.extract(markdown)

        assertEquals(
            listOf(
                "images/logo.png",
                "../assets/banner.jpg",
            ),
            result,
        )
    }

    @Test
    fun `extract supports absolute urls`() {
        val markdown =
            """
            ![Image](https://example.com/image.png)
            """.trimIndent()

        val result =
            extractor.extract(markdown)

        assertEquals(
            listOf(
                "https://example.com/image.png",
            ),
            result,
        )
    }

    @Test
    fun `extract ignores normal links`() {
        val markdown =
            """
            [OpenAI](https://example.com)
            
            ![Image](image.png)
            """.trimIndent()

        val result =
            extractor.extract(markdown)

        assertEquals(
            listOf("image.png"),
            result,
        )
    }
}
