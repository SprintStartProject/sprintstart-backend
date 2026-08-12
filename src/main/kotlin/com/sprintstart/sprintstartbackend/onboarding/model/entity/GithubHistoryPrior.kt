package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.MapKeyColumn
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * What a user's existing work in the project's connected repositories suggests, as a prior for
 * their skill assessment.
 *
 * Deliberately stores only the **derived** signal -- counts per bucket -- and never the underlying
 * activity: no titles, no bodies, no commit messages, nothing from outside the repositories the
 * project has already connected. A user can see exactly this, which is the whole record of what
 * was inferred about them, and revoking consent deletes the row (any placement already earned
 * stays, because it is theirs).
 *
 * Signal keys are namespaced by what they describe, e.g. `repo:owner/name`, `type:PULL_REQUEST`,
 * `label:good first issue`.
 */
@Entity
@Table(name = "github_history_priors")
class GithubHistoryPrior(
    @Id
    @Column(name = "user_id")
    val userId: UUID,
    @ElementCollection
    @CollectionTable(
        name = "github_history_prior_signals",
        joinColumns = [JoinColumn(name = "user_id")],
    )
    @MapKeyColumn(name = "signal_key")
    @Column(name = "signal_count", nullable = false)
    val signals: MutableMap<String, Int> = mutableMapOf(),
    @Column(name = "computed_at", nullable = false)
    var computedAt: Instant = Instant.now(),
)
