package com.sprintstart.sprintstartbackend.upload.service

import com.sprintstart.sprintstartbackend.upload.model.entity.LinkedImage
import com.sprintstart.sprintstartbackend.upload.model.entity.UploadedArtifact
import com.sprintstart.sprintstartbackend.upload.repository.LinkedImageRepository
import org.springframework.stereotype.Service
import java.nio.file.Paths

/**
 * Creates links between uploaded markdown files and uploaded image files they reference.
 *
 * Only images that are present in the same upload batch are linked. Markdown image paths are
 * normalized to their filename so relative paths in markdown can still match uploaded image
 * artifact filenames.
 */
@Service
class ArtifactLinkingService(
    private val linkedImageRepository: LinkedImageRepository,
    private val extractor: MarkdownImageReferenceExtractor,
) {
    /**
     * Links markdown files only to image artifacts from the same upload batch.
     *
     * Markdown paths are reduced to their filename before matching, so `images/a.png` and `a.png`
     * both resolve to an uploaded artifact named `a.png`. References that cannot be resolved in the
     * batch are ignored rather than failing the upload.
     *
     * @param markdownArtifacts Markdown artifacts paired with the text content uploaded in the
     * same batch.
     * @param uploadedArtifactsByFilename Batch artifacts keyed by their original filename.
     * @return The link rows persisted for resolvable markdown image references.
     */
    fun linkMarkdownImages(
        markdownArtifacts: List<Pair<UploadedArtifact, String>>,
        uploadedArtifactsByFilename: Map<String, UploadedArtifact>,
    ): Set<LinkedImage> {
        val linkedImages = mutableSetOf<LinkedImage>()
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

                val linkedImage = LinkedImage(
                    markdownArtifact = artifact,
                    originalPath = imagePath,
                    imageArtifact = imageArtifact,
                )

                linkedImageRepository.save(linkedImage)

                linkedImages.add(linkedImage)
            }
        }
        return linkedImages.toSet()
    }
}
