package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "phase_check_options")
class PhaseCheckOption(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "question_id", nullable = false)
    val question: PhaseCheckQuestion,
    @Column(nullable = false)
    var position: Int,
    @Column(nullable = false, columnDefinition = "TEXT")
    var label: String,
    @Column(nullable = false)
    var correct: Boolean,
)
