package com.sprintstart.sprintstartbackend.insights.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.util.UUID

/**
 * A single sample question that belongs to a [FaqGroup].
 *
 * Sample questions are a representative subset of the cluster, not the full history. The text is
 * stored as delivered by the AI service, which is responsible for stripping personally identifiable
 * information before the questions reach this module.
 */
@Entity
class FaqQuestion(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, columnDefinition = "TEXT")
    val text: String,
    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    val group: FaqGroup,
)
