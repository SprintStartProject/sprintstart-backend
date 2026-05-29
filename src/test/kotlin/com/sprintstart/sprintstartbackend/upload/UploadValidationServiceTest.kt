package com.sprintstart.sprintstartbackend.upload

import com.sprintstart.sprintstartbackend.upload.service.UploadValidationService
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile

class UploadValidationServiceTest {
    private val validationService =
        UploadValidationService(
            maxFileSizeBytes = 1024 * 1024,
        )

    @Test
    fun `should validate correct markdown file`() {
        val file = MockMultipartFile(
            "file",
            "intro.md",
            "text/markdown",
            "# Hello".toByteArray(),
        )

        validationService.validate(file)
    }

    @Test
    fun `should reject unsupported extension`() {
        val file = MockMultipartFile(
            "file",
            "virus.exe",
            "application/octet-stream",
            "bad".toByteArray(),
        )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            validationService.validate(file)
        }
    }

    @Test
    fun `should reject invalid filename`() {
        val file = MockMultipartFile(
            "file",
            "../secret.md",
            "text/markdown",
            "# bad".toByteArray(),
        )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            validationService.validate(file)
        }
    }

    @Test
    fun `should reject oversized file`() {
        val bytes = ByteArray(2 * 1024 * 1024)

        val file = MockMultipartFile(
            "file",
            "large.md",
            "text/markdown",
            bytes,
        )

        assertThrows(
            IllegalArgumentException::class.java,
        ) {
            validationService.validate(file)
        }
    }
}
