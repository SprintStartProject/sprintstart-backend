package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.AiConfig
import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.CryptoConfig
import com.sprintstart.sprintstartbackend.GithubConfig
import com.sprintstart.sprintstartbackend.UploadConfig
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockMultipartFile

class UploadValidationServiceTest {
    private val service =
        UploadValidationService(
            applicationConfig = ApplicationConfig(
                ai = AiConfig(baseUrl = "http://unused"),
                github = GithubConfig(
                    baseUrl = "https://api.github.com",
                    repoBaseUrl = "https://api.github.com/repos",
                    cron = "0 0 * * *",
                ),
                crypto = CryptoConfig(masterKey = "unused", salt = "unused"),
                upload = UploadConfig(directory = "/tmp/uploads", maxFileSizeBytes = 100),
            ),
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
            "archive.zip",
            "application/zip",
            "hello".toByteArray(),
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            service.validate(file)
        }

        assertEquals("Unsupported file extension: zip", ex.message)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "notes.txt",
            "guide.pdf",
            "Service.java",
            "native.cpp",
            "component.tsx",
            "Dockerfile",
            "script.sh",
            "config.yaml",
        ],
    )
    fun `accepts text document and code upload extensions`(filename: String) {
        val file = MockMultipartFile(
            "files",
            filename,
            "application/octet-stream",
            "content".toByteArray(),
        )

        assertDoesNotThrow {
            service.validate(file)
        }
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
