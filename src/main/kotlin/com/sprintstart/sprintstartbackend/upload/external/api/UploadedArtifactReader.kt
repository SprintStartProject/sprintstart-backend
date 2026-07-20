package com.sprintstart.sprintstartbackend.upload.external.api

import java.util.UUID

interface UploadedArtifactReader {
    fun readText(
        artifactId: UUID,
    ): String

    fun readBytes(
        artifactId: UUID,
    ): ByteArray
}
