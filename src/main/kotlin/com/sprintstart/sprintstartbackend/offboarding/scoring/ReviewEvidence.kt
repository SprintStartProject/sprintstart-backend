package com.sprintstart.sprintstartbackend.offboarding.scoring

import com.sprintstart.sprintstartbackend.ingestion.external.ContributionEvidenceApi
import com.sprintstart.sprintstartbackend.ingestion.external.model.dto.FileReview
import com.sprintstart.sprintstartbackend.shared.ids.ProjectId
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * The evidence of who reviewed which file, read from the pull requests ingestion recorded.
 *
 * A reviewer knows a file less than its author does, so a review never founds a file. Paths are those
 * the pull request changed at the time, so a file renamed since is not joined to its earlier reviews.
 */
@Component
class ReviewEvidence(
    private val contributions: ContributionEvidenceApi,
) : EvidenceSource {
    override val id: SourceId = SourceId.REVIEWS

    override fun histories(projectId: ProjectId, asOf: Instant): List<FileHistory> =
        contributions
            .fileReviews(projectId, asOf)
            .groupBy { it.repository to it.path }
            .map { (file, reviews) -> FileHistory(file.first, file.second, reviews.map(::touchOf)) }

    private fun touchOf(review: FileReview) =
        Touch(PersonKey.parse(review.reviewerLogin), review.reviewedAt, id, founding = false)
}
