package com.sprintstart.sprintstartbackend.onboarding.blueprint.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "blueprint_check_options")
data class BlueprintCheckOption(
    @Id
    val id: UUID = UUID.randomUUID(),
    @ManyToOne
    @JoinColumn(name = "blueprint_question_id", nullable = false)
    val blueprintCheckQuestion: BlueprintCheckQuestion,
    @Column(nullable = false)
    var position: Int,
    @Column(nullable = false, columnDefinition = "TEXT")
    var label: String,
    @Column(nullable = false)
    var correct: Boolean,
)
