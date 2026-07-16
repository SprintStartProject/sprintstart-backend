package com.sprintstart.sprintstartbackend.upload.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

/**
 * Validates uploaded files before storage.
 *
 * The service keeps upload constraints close to the upload module: files must be non-empty, below
 * the configured size limit, have a safe filename, and use an allowed extension.
 */
@Service
class UploadValidationService(
    @Value("\${app.upload.max-file-size-bytes}")
    private val maxFileSizeBytes: Long,
) {
    private val allowedExtensions = setOf(
        "md",
        "png",
        "jpg",
        "jpeg",
        "webp",
    )

    /**
     * Applies all upload acceptance checks before storage is attempted.
     *
     * Filenames are rejected when missing or when they contain path traversal or path separator
     * characters, because the local storage implementation writes the original filename under an
     * artifact-specific directory.
     *
     * @param file The multipart file submitted by the caller.
     * @throws IllegalArgumentException when the file is empty, too large, has an unsafe filename,
     * or uses an unsupported extension.
     */
    fun validate(file: MultipartFile) {
        validateEmpty(file)

        validateSize(file)

        validateFilename(file)

        validateExtension(file)
    }

    private fun validateEmpty(file: MultipartFile) {
        if (file.isEmpty) {
            throw IllegalArgumentException("File is empty")
        }
    }

    private fun validateSize(file: MultipartFile) {
        if (file.size > maxFileSizeBytes) {
            throw IllegalArgumentException(
                "File exceeds maximum allowed size",
            )
        }
    }

    private fun validateFilename(file: MultipartFile) {
        val filename = file.originalFilename
            ?: throw IllegalArgumentException("Missing filename")

        if (
            filename.contains("..") ||
            filename.contains("/") ||
            filename.contains("\\")
        ) {
            throw IllegalArgumentException("Invalid filename")
        }
    }

    private fun validateExtension(file: MultipartFile) {
        val filename = file.originalFilename
            ?: throw IllegalArgumentException("Missing filename")

        val extension = filename
            .substringAfterLast(
                delimiter = ".",
                missingDelimiterValue = "",
            ).lowercase()

        if (extension !in allowedExtensions) {
            throw IllegalArgumentException(
                "Unsupported file extension: $extension",
            )
        }
    }
}
