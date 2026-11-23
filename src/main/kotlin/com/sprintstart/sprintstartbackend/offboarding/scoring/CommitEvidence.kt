package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Component
import java.time.Instant

/** The evidence of who changed which file, read from the commits ingestion recorded. */
@Component
class CommitEvidence(
    private val contributions: ContributionEvidenceApi,
) : EvidenceSource {
    override val id: SourceId = SourceId.COMMITS

    override fun histories(projectId: ProjectId, asOf: Instant): List<FileHistory> =
        FileHistories.build(contributions.fileContributions(projectId, asOf))
}
