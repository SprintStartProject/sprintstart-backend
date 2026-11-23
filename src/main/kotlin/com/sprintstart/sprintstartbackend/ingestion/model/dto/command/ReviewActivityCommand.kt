package com.sprintstart.sprintstartbackend.ingestion.model.dto.command

import com.sprintstart.sprintstartbackend.ingestion.model.entity.ReviewState
import java.time.Instant

/**
 * The reviews one stored pull request received and the files it changed, as a neutral command every
 * Git provider's mapper produces.
 *
 * @property sourceId The source id of the pull request artifact the reviews belong to.
 * @property repository The key of the repository the pull request belongs to, for example
 *           `github:owner/repo`.
 * @property reviews The submitted reviews by anyone other than the pull request's author. A
 *           reviewer may appear more than once.
 * @property paths The files the pull request changed. Empty when they are not all known, because a
 *           reviewer cannot be said to know the files that were cut off.
 */
data class ReviewActivityCommand(
    val sourceId: String,
    val repository: String,
    val reviews: List<SubmittedReview>,
    val paths: List<String>,
)

/**
 * One review that was submitted.
 *
 * @property reviewer The reviewer's account login, lower-cased.
 * @property state How the review ended.
 * @property submittedAt When it was submitted.
 */
data class SubmittedReview(
    val reviewer: String,
    val state: ReviewState,
    val submittedAt: Instant,
)
