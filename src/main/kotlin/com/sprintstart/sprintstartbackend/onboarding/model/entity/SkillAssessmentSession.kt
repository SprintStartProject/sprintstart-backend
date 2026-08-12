package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.SkillAssessmentSessionStatus
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A hire's in-progress (or completed) adaptive skill-assessment interview, for one project.
 *
 * The AI interviewer (Seam 1) is stateless — this session is what makes the conversation durable
 * across `/start`/`/answer` calls: [turns] is the transcript the backend replays as `history` on
 * every AI call. A hire has at most one meaningfully active session per project at a time; `/start`
 * resumes an [SkillAssessmentSessionStatus.IN_PROGRESS] session for that project rather than
 * spawning a duplicate.
 *
 * Scoped per project because the candidate competencies it interviews over are: what a project
 * actually teaches (its live [CompetencyModule]s), not the whole global catalog. The placement it
 * writes still lands on the global, per-competency [UserCompetencyState] ledger -- "earn once,
 * transfers across projects" -- only the interview itself repeats per project.
 */
@Entity
@Table(name = "skill_assessment_sessions")
class SkillAssessmentSession(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "project_id", nullable = false)
    val projectId: UUID,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SkillAssessmentSessionStatus = SkillAssessmentSessionStatus.IN_PROGRESS,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(nullable = false)
    var updatedAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "session", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("turnIndex ASC")
    val turns: MutableList<SkillAssessmentTurn> = mutableListOf(),
)
