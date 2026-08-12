package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.VerificationType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.util.UUID

/**
 * A graded check — the gate on a competency.
 *
 * Owned by a [CompetencyModule]: one shared check per module, so two hires proving the same
 * competency are held to the same bar. [VerificationAttempt] rows point at a [Verification] rather
 * than at its owner, so a check can change owner without touching a single attempt.
 *
 * A module has at most one [Verification] (enforced by the unique constraint), referenced by a
 * plain FK rather than a bidirectional JPA relationship, so this entity can be created/edited
 * independently without touching the module's cascade footprint.
 * [competencyKey] ties this check to a [Competency] by its stable key rather than a FK, so
 * deleting a competency cannot take a check's history with it, and [level] is the
 * target proficiency level being verified, aligned with the AI service's `beginner..expert` ladder
 * (see `AssessmentService.LEVEL_RANKS` for the same scale used elsewhere in this module).
 *
 * [rubric] is required for [VerificationType.KNOWLEDGE] (graded by the AI service against the
 * owner's lesson content as grounded evidence) and [canonicalAnswer] for
 * [VerificationType.EXACT] (graded locally); neither is meaningful for [VerificationType.ATTEST].
 * [rubric] is also required for [VerificationType.ARTIFACT], alongside [repositoryConnectionId] --
 * the GitHub repository connection (owned by the `connectors.github` module, referenced by plain
 * id rather than a cross-module JPA relation) a hire's submitted PR number is resolved against.
 */
@Entity
@Table(
    name = "verifications",
    uniqueConstraints = [
        UniqueConstraint(name = "uq_verifications_module", columnNames = ["module_id"]),
    ],
)
class Verification(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "module_id", nullable = false)
    val moduleId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: VerificationType,
    @Column(nullable = false, columnDefinition = "TEXT")
    var prompt: String,
    @Column(nullable = true, columnDefinition = "TEXT")
    var rubric: String? = null,
    @Column(name = "canonical_answer", nullable = true, columnDefinition = "TEXT")
    var canonicalAnswer: String? = null,
    @Column(name = "repository_connection_id", nullable = true)
    var repositoryConnectionId: UUID? = null,
    @Column(name = "competency_key", nullable = false)
    var competencyKey: String,
    @Column(nullable = false)
    var level: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
