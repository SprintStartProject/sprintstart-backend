package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CheckQuestionType
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "phase_check_questions")
class PhaseCheckQuestion(
    id: UUID = UUID.randomUUID(),
    phase: OnboardingPhase,
    @Column(nullable = false)
    var position: Int,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: CheckQuestionType,
    @Column(nullable = false, columnDefinition = "TEXT")
    var question: String,
    title: String = question,
    @Column(nullable = true, columnDefinition = "TEXT")
    var explanation: String? = null,
    // Expected answer for SHORT_TEXT questions, null for MULTIPLE_CHOICE
    @Column(nullable = true, columnDefinition = "TEXT")
    var correctAnswer: String? = null,
    @OneToMany(
        mappedBy = "question",
        cascade = [CascadeType.ALL],
        orphanRemoval = true,
    )
    @OrderBy("position ASC")
    val options: MutableList<PhaseCheckOption> = mutableListOf(),
    graphX: Double? = null,
    graphY: Double? = null,
) : OnboardingSubGraphNode(
        id = id,
        phase = phase,
        title = title,
        graphX = graphX,
        graphY = graphY,
    )
