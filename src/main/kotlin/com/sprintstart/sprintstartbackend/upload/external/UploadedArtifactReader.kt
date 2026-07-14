package com.sprintstart.sprintstartbackend.upload.external

import java.util.UUID

interface UploadedArtifactReader {
    fun readText(
        artifactId: UUID,
    ): String
}
