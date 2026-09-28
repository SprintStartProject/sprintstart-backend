package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * One submission of a knowledge-check question by a user.
 *
 * Questions are first-class nodes of the onboarding subgraph, so attempts belong to the
 * question rather than to a phase-level check: a question counts as passed once any of its
 * attempts was correct, and every wrong attempt leaves it open for another try.
 *
 * The selected options and free-text answer are snapshots — they must survive edits to the
 * question and its options, so [questionId] is a plain UUID and the options are stored by id
 * rather than as JPA associations.
 */
@Entity
@Table(name = "question_attempts")
class QuestionAttempt(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false)
    val questionId: UUID,
    @Column(nullable = false)
    val userId: UUID,
    @Column(nullable = false)
    val correct: Boolean,
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_attempt_options", joinColumns = [JoinColumn(name = "attempt_id")])
    @Column(name = "option_id")
    val selectedOptionIds: MutableList<UUID> = mutableListOf(),
    @Column(nullable = true, columnDefinition = "TEXT")
    val textAnswer: String? = null,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
)
