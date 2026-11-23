package com.sprintstart.sprintstartbackend.ingestion.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import java.time.Instant
import java.util.UUID

/**
 * One reviewer's review of one file of a pull request.
 *
 * A review is of the whole pull request, but what it says about a person is which files they have
 * seen, so it is recorded per file: a row for every file the pull request changed, for every
 * reviewer who submitted a review. A reviewer who reviewed more than once is recorded with their
 * latest review. The pull request's own author is never a reviewer of it.
 *
 * Like [FileChange] this is evidence, not an artifact, and it can be rebuilt at any time by reading
 * the pull request again. It lives and dies with its pull request artifact.
 *
 * @property id The row's identity, assigned when the row is first saved, so it is `null` only on a
 *           row that has not been stored yet.
 * @property artifact The pull request artifact the review belongs to.
 * @property repository The key of the repository the pull request belongs to, for example
 *           `github:owner/repo`, so a path is only ever compared within its own repository.
 * @property path The path of a file the pull request changed.
 * @property reviewerLogin The reviewer's account login at the source, lower-cased.
 * @property state How the reviewer's latest review ended.
 * @property reviewedAt When the reviewer submitted that review.
 */
@Entity
@Table(
    name = "review_activity",
    indexes = [
        Index(name = "idx_review_activity_artifact", columnList = "artifact_id"),
        Index(name = "idx_review_activity_repository", columnList = "repository"),
    ],
)
class ReviewActivity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val artifact: Artifact,
    @Column(nullable = false)
    val repository: String,
    @Column(nullable = false, columnDefinition = "TEXT")
    val path: String,
    @Column(name = "reviewer_login", nullable = false)
    val reviewerLogin: String,
    @Enumerated(EnumType.STRING)
    @Column(name = "review_state", nullable = false)
    val state: ReviewState,
    @Column(name = "reviewed_at", nullable = false)
    val reviewedAt: Instant,
)
