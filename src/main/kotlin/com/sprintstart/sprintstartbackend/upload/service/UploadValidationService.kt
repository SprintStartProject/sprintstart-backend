package com.sprintstart.sprintstartbackend.upload.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

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
                "File exceeds maximum allowed size"
            )
        }
    }

    private fun validateFilename(file: MultipartFile) {

        val filename = file.originalFilename
            ?: throw IllegalArgumentException("Missing filename")

        if (filename.contains("..")) {
            throw IllegalArgumentException("Invalid filename")
        }

        if (filename.contains("/")) {
            throw IllegalArgumentException("Invalid filename")
        }

        if (filename.contains("\\")) {
            throw IllegalArgumentException("Invalid filename")
        }
    }

    private fun validateExtension(file: MultipartFile) {

        val filename = file.originalFilename
            ?: throw IllegalArgumentException("Missing filename")

        val extension = filename.substringAfterLast(
            delimiter = ".",
            missingDelimiterValue = "",
        ).lowercase()

        if (extension !in allowedExtensions) {
            throw IllegalArgumentException(
                "Unsupported file extension: $extension"
            )
        }
    }
}