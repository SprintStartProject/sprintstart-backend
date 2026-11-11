package com.sprintstart.sprintstartbackend.ingestion.model.mapper

import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestFetchedEvent
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestFiles
import com.sprintstart.sprintstartbackend.connectors.git.github.external.events.pullrequests.GithubPullRequestReview
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.ReviewActivityCommand
import com.sprintstart.sprintstartbackend.ingestion.model.dto.command.SubmittedReview
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ArtifactType
import com.sprintstart.sprintstartbackend.ingestion.model.entity.ReviewState
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildGithubRepositoryKey
import com.sprintstart.sprintstartbackend.ingestion.model.mapper.SourceIdFactory.buildSourceId
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Translates a fetched GitHub pull request into the neutral command that records its reviews.
 *
 * Kept apart from the artifact mapper: that one decides what a pull request looks like as an
 * artifact, this one decides which of its reviews say something about who has seen which file. The
 * pull request's source id is built by the same factory, so the reviews always attach to the
 * artifact stored for that pull request.
 */
@Component
class ReviewActivityMapper {
    /**
     * Maps a GitHub pull request.
     *
     * @param event The fetched pull request.
     */
    fun toCommand(event: GithubPullRequestFetchedEvent): ReviewActivityCommand =
        ReviewActivityCommand(
            sourceId = buildSourceId(
                repositoryOwner = event.repositoryOwner,
                repositoryName = event.repositoryName,
                type = ArtifactType.PULL_REQUEST,
                unique = event.number.toString(),
            ),
            repository = buildGithubRepositoryKey(event.repositoryOwner, event.repositoryName),
            reviews = event.reviews.orEmpty().mapNotNull { toSubmittedReview(it, event.author) },
            paths = pathsOf(event.files),
        )

    /**
     * A review counts when it was submitted, ended in a state that says the reviewer looked at the
     * change, and was written by someone who is neither unknown nor the pull request's own author:
     * an author replying to their own pull request has not reviewed it.
     */
    private fun toSubmittedReview(review: GithubPullRequestReview, pullRequestAuthor: String?): SubmittedReview? {
        val reviewer = review.author?.lowercase()?.takeUnless { it == pullRequestAuthor?.lowercase() }
        val state = ReviewState.entries.firstOrNull { it.name == review.state }
        val submittedAt = review.submittedAt?.let(Instant::parse)

        return if (reviewer == null || state == null || submittedAt == null) {
            null
        } else {
            SubmittedReview(reviewer, state, submittedAt)
        }
    }

    private fun pathsOf(files: GithubPullRequestFiles): List<String> =
        when (files) {
            is GithubPullRequestFiles.Listed -> files.paths
            GithubPullRequestFiles.Incomplete -> emptyList()
        }
}
