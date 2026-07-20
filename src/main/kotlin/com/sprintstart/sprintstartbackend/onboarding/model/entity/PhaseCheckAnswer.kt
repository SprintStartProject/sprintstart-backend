package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "phase_check_answers")
class PhaseCheckAnswer(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "attempt_id", nullable = false)
    val attempt: PhaseCheckAttempt,
    // Not a foreign key: answers must survive question edits as attempt history
    @Column(nullable = false)
    val questionId: UUID,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "phase_check_answer_options", joinColumns = [JoinColumn(name = "answer_id")])
    @Column(name = "option_id")
    val selectedOptionIds: MutableList<UUID> = mutableListOf(),
    @Column(nullable = true, columnDefinition = "TEXT")
    val textAnswer: String? = null,
    @Column(nullable = false)
    val correct: Boolean,
)
