package com.sprintstart.sprintstartbackend.ingestion.service

import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ReviewActivityCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.SubmittedReview
import com.sprintstart.sprintstartbackend.ingestion.model.entity.Artifact
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ReviewActivity
import com.sprintstart.sprintstartbackend.ingestion.model.exceptions.EvidenceArtifactNotFoundException
import com.sprintstart.sprintstartbackend.ingestion.repository.ArtifactRepository
import com.sprintstart.sprintstartbackend.ingestion.repository.ReviewActivityRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Records which files each reviewer of a stored pull request has seen.
 *
 * The record is a projection of the pull request, so recording it again replaces what was stored. A
 * pull request is fetched again whenever it changes, and a new review has to replace the reviewer's
 * old one rather than sit beside it.
 */
@Service
class ReviewActivityService(
    private val artifactRepository: ArtifactRepository,
    private val reviewActivityRepository: ReviewActivityRepository,
) {
    /**
     * Replaces the reviews stored for a pull request.
     *
     * Each reviewer is recorded once per file with their latest review. A pull request whose files
     * are not all known has no rows, because a reviewer cannot be said to know files that were cut
     * off.
     *
     * @param command The pull request, its submitted reviews and the files it changed.
     * @throws EvidenceArtifactNotFoundException when the pull request artifact is not stored.
     */
    @Transactional
    fun record(command: ReviewActivityCommand) {
        val artifact = artifactRepository.findBySourceId(command.sourceId)
            ?: throw EvidenceArtifactNotFoundException(command.sourceId)

        reviewActivityRepository.deleteAllByArtifactId(artifact.id)

        val rows = latestReviews(command.reviews).flatMap { review ->
            command.paths.map { path -> toEntity(artifact, command.repository, review, path) }
        }
        reviewActivityRepository.saveAll(rows)
    }

    private fun latestReviews(reviews: List<SubmittedReview>): List<SubmittedReview> =
        reviews
            .groupBy { it.reviewer }
            .values
            .map { byReviewer -> byReviewer.maxBy { it.submittedAt } }

    private fun toEntity(
        artifact: Artifact,
        repository: String,
        review: SubmittedReview,
        path: String,
    ): ReviewActivity =
        ReviewActivity(
            artifact = artifact,
            repository = repository,
            path = path,
            reviewerLogin = review.reviewer,
            state = review.state,
            reviewedAt = review.submittedAt,
        )
}
