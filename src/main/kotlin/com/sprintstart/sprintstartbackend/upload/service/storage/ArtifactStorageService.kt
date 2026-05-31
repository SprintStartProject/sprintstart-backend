package com.sprintstart.sprintstartbackend.upload.service.storage

import org.springframework.web.multipart.MultipartFile
import java.util.UUID

interface ArtifactStorageService {
    fun store(
        file: MultipartFile,
        artifactId: UUID,
    ): String

    fun delete(
        storagePath: String,
    )
}
