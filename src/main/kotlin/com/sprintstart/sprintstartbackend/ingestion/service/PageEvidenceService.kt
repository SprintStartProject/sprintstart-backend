package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.connectors.confluence.external.ingestion.ConfluencePageEditCommand
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileKind
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.PageEdit
import com.sprintstart.sprintstartbackend.ingestion.repository.PageEditRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Keeps what a documentation page says about who knows it: who saved which version, and how much it holds.
 *
 * A page is identified like a file, by the key of its space and its title, so the risk analysis treats it
 * like any other file of the project.
 */
@Service
internal class PageEvidenceService(
    private val edits: PageEditRepository,
    private val complexity: FileComplexityService,
) {
    /**
     * Records the evidence of one page read from its source.
     *
     * The versions replace those recorded before, so reading a page again never counts a version twice.
     * When the source would not give the versions, the ones recorded before stay. The page is measured by
     * its words, and a page that was renamed forgets the measurement under its old title.
     *
     * @param page The stored page artifact.
     * @param repositoryKey The key of the page's space, for example `confluence:ARCH`.
     * @param previousTitle The title the page had before this read; `null` for a new page.
     * @param versions Who saved which version; `null` when the source would not say.
     */
    @Transactional
    fun record(
        page: Artifact,
        repositoryKey: String,
        previousTitle: String?,
        versions: List<ConfluencePageEditCommand>?,
    ) {
        val title = page.title ?: page.sourceId
        if (previousTitle != null && previousTitle != title) complexity.forget(repositoryKey, previousTitle)
        complexity.measure(repositoryKey, title, page.content.orEmpty(), FileKind.PROSE)
        if (versions == null) return
        edits.deleteAllByArtifactId(page.id)
        edits.saveAll(
            versions.map { PageEdit(null, page, repositoryKey, it.versionNumber, it.authorAccountId, it.editedAt) },
        )
    }
}
