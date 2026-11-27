package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.PageEditReading
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * The evidence of who wrote which documentation page, read from the page versions ingestion recorded.
 *
 * A page is a file of its space, named by its current title, so the scoring ranks pages next to code.
 * Its authors are Atlassian accounts for Confluence and Notion users for Notion, which count as the same
 * person as a commit email only once a manager linked the two.
 */
@Component
class PageEditEvidence(
    private val contributions: ContributionEvidenceApi,
) : EvidenceSource {
    override val id: SourceId = SourceId.PAGE_EDITS

    override fun histories(projectId: ProjectId, asOf: Instant): List<FileHistory> =
        contributions
            .pageEdits(projectId, asOf)
            .groupBy { it.repository to it.path }
            .map { (page, edits) -> FileHistory(page.first, page.second, edits.map(::touchOf)) }

    private fun touchOf(edit: PageEditReading) = Touch(personOf(edit), edit.editedAt, id)

    private fun personOf(edit: PageEditReading): PersonKey =
        if (edit.repository.startsWith(NOTION)) {
            PersonKey("$NOTION${edit.authorAccountId.trim().lowercase()}")
        } else {
            PersonKey.ofAtlassian(edit.authorAccountId)
        }

    private companion object {
        const val NOTION = "notion:"
    }
}
