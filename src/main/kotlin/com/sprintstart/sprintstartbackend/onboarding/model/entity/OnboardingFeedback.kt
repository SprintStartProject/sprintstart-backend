package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A hire's reaction to a piece of onboarding content.
 *
 * ⚠️ Attached to a [ModulePage], which is what makes the content-quality loop mean anything: the
 * page is shared, so "this didn't help" is a signal about the material everybody reads. Hung off
 * anything per-user, three hires disliking the same lesson would produce three unrelated counts
 * of one.
 */
@Entity
@Table(name = "onboarding_feedback")
class OnboardingFeedback(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val userId: UUID,
    // Nullable: feedback can be about onboarding in general, not a specific page.
    // Feedback hangs off either a blueprint-era step or a module page, never both. Both sides stay
    // nullable so the two onboarding models can be in the database at once.
    @ManyToOne
    @JoinColumn(name = "step_id", nullable = true)
    var step: OnboardingStep? = null,
    @ManyToOne
    @JoinColumn(name = "page_id", nullable = true)
    var page: ModulePage? = null,
    @Column(nullable = true)
    var helpful: Boolean? = null,
    @Column(nullable = false, columnDefinition = "TEXT")
    var message: String,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var read: Boolean = false,
)
