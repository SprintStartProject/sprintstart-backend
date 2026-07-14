package com.sprintstart.sprintstartbackend.upload.service.storage

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Filesystem implementation of upload artifact storage.
 *
 * Each artifact is stored under a directory named with the artifact id. Deletion removes the file
 * and then removes the artifact directory when it becomes empty.
 */
@Service
class LocalFileStorageService(
    @Value("\${app.upload.directory}")
    private val uploadDirectory: String,
) : ArtifactStorageService {
    /**
     * Stores the file under a directory named with the artifact id.
     *
     * Reusing an existing filename inside the same artifact directory replaces the previous bytes,
     * matching the storage contract used by retry-like flows.
     *
     * @param file The multipart file whose bytes should be copied.
     * @param artifactId The uploaded artifact id used as the storage directory name.
     * @return The filesystem path persisted on the uploaded artifact row.
     * @throws IllegalArgumentException when the multipart file has no original filename.
     * @throws java.io.IOException when the directory cannot be created or the file cannot be copied.
     */
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

    /**
     * Deletes the stored file and removes its artifact directory when it becomes empty.
     *
     * Missing files are treated as already deleted; unexpected filesystem failures still propagate
     * so callers can record the deletion as failed.
     *
     * @param storagePath The filesystem path previously returned by [store].
     * @throws java.io.IOException when the path or parent directory cannot be inspected or removed.
     */
    override fun delete(
        storagePath: String,
    ) {
        val filePath = Paths.get(
            storagePath,
        )

        Files.deleteIfExists(
            filePath,
        )

        val parentDirectory =
            filePath.parent

        if (
            parentDirectory != null &&
            Files.exists(parentDirectory)
        ) {
            Files.list(parentDirectory).use { files ->

                if (files.findAny().isEmpty) {
                    Files.deleteIfExists(
                        parentDirectory,
                    )
                }
            }
        }
    }
}
