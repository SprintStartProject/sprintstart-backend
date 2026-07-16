package com.sprintstart.sprintstartbackend.upload.service

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile

class UploadValidationServiceTest {
    private val service =
        UploadValidationService(
            maxFileSizeBytes = 100,
        )

    @Test
    fun `accepts valid markdown file`() {
        val file = MockMultipartFile(
            "files",
            "readme.md",
            "text/markdown",
            "# Hello".toByteArray(),
        )

        assertDoesNotThrow {
            service.validate(file)
        }
    }

    @Test
    fun `rejects empty file`() {
        val file = MockMultipartFile(
            "files",
            "readme.md",
            "text/markdown",
            ByteArray(0),
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.validate(file)
        }

        assertEquals("File is empty", ex.message)
    }

    @Test
    fun `rejects oversized file`() {
        val file = MockMultipartFile(
            "files",
            "readme.md",
            "text/markdown",
            ByteArray(101),
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.validate(file)
        }

        assertEquals("File exceeds maximum allowed size", ex.message)
    }

    @Test
    fun `rejects path traversal`() {
        val file = MockMultipartFile(
            "files",
            "../secret.md",
            "text/markdown",
            "# Hello".toByteArray(),
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.validate(file)
        }

        assertEquals("Invalid filename", ex.message)
    }

    @Test
    fun `rejects slash in filename`() {
        val file = MockMultipartFile(
            "files",
            "folder/file.md",
            "text/markdown",
            "# Hello".toByteArray(),
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.validate(file)
        }

        assertEquals("Invalid filename", ex.message)
    }

    @Test
    fun `rejects unsupported extension`() {
        val file = MockMultipartFile(
            "files",
            "notes.txt",
            "text/plain",
            "hello".toByteArray(),
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.validate(file)
        }

        assertEquals("Unsupported file extension: txt", ex.message)
    }

    @Test
    fun `accepts png`() {
        val file = MockMultipartFile(
            "files",
            "image.png",
            "image/png",
            byteArrayOf(1, 2, 3),
        )

        assertDoesNotThrow {
            service.validate(file)
        }
    }

    @Test
    fun `accepts jpg`() {
        val file = MockMultipartFile(
            "files",
            "image.jpg",
            "image/jpeg",
            byteArrayOf(1, 2, 3),
        )

        assertDoesNotThrow {
            service.validate(file)
        }
    }

    @Test
    fun `accepts webp`() {
        val file = MockMultipartFile(
            "files",
            "image.webp",
            "image/webp",
            byteArrayOf(1, 2, 3),
        )

        assertDoesNotThrow {
            service.validate(file)
        }
    }
}
