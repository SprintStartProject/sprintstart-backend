package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.model.entity.ArtifactImage
import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import com.sprintstart.sprintstartbackend.upload.repository.ArtifactImageRepository
import org.springframework.stereotype.Service
import java.nio.file.Paths

@Service
class ArtifactLinkingService(
    private val artifactImageRepository: ArtifactImageRepository,
    private val extractor: MarkdownImageReferenceExtractor,
) {
    fun linkMarkdownImages(
        markdownArtifacts: List<Pair<UploadedArtifact, String>>,
        uploadedArtifactsByFilename: Map<String, UploadedArtifact>,
    ) {
        markdownArtifacts.forEach { (artifact, markdownContent) ->

            val imagePaths = extractor.extract(markdownContent)

            imagePaths.forEach { imagePath ->

                val normalizedFilename =
                    Paths
                        .get(imagePath)
                        .fileName
                        .toString()

                val imageArtifact =
                    uploadedArtifactsByFilename[normalizedFilename]
                        ?: return@forEach

                val artifactImage = ArtifactImage(
                    artifact = artifact,
                    originalPath = imagePath,
                    imageArtifact = imageArtifact,
                )

                artifactImageRepository.save(artifactImage)
            }
        }
    }
}
