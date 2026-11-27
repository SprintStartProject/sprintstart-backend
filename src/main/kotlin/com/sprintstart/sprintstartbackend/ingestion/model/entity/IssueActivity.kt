package com.sprintstart.sprintstartbackend.ingestion.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
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
 * One person working on a ticket at one moment: reporting it, being assigned, changing it or commenting.
 *
 * This is evidence, not an artifact, like [FileChange] for commits: it is rebuilt from the issue whenever
 * the issue is read and lives and dies with the issue artifact. Commits name tickets by their key, so the
 * key is kept to join the two.
 *
 * @property id The row's identity, assigned when the row is first saved.
 * @property artifact The issue artifact the activity belongs to.
 * @property issueKey The ticket's key, for example `APP-42`.
 * @property accountId The person's account ID at the tracker; `null` for trackers that do not send one.
 * @property displayName The person's name as the tracker shows it.
 * @property at When the person did it.
 */
@Entity
@Table(
    name = "issue_activity",
    indexes = [
        Index(name = "idx_issue_activity_artifact", columnList = "artifact_id"),
        Index(name = "idx_issue_activity_key", columnList = "issue_key"),
    ],
)
class IssueActivity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "artifact_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    val artifact: Artifact,
    @Column(name = "issue_key", nullable = false)
    val issueKey: String,
    @Column(name = "account_id")
    val accountId: String?,
    @Column(name = "display_name", nullable = false)
    val displayName: String,
    @Column(nullable = false)
    val at: Instant,
)
