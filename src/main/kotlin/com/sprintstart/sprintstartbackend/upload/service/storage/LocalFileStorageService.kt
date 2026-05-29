package com.sprintstart.sprintstartbackend.upload.service.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

@Service
class LocalFileStorageService(
    @Value("\${app.upload.directory}")
    private val uploadDirectory: String,
) : ArtifactStorageService {
    override fun store(
        file: MultipartFile,
        artifactId: UUID,
    ): String {
        val artifactDirectory = Paths.get(
            uploadDirectory,
            artifactId.toString(),
        )

        Files.createDirectories(artifactDirectory)

        val filename = file.originalFilename
            ?: throw IllegalArgumentException("File name is missing")

        val targetPath = artifactDirectory.resolve(filename)

        Files.copy(
            file.inputStream,
            targetPath,
            StandardCopyOption.REPLACE_EXISTING,
        )

        return targetPath.toString()
    }
}
