package com.sprintstart.sprintstartbackend.insights.model.entity

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import java.time.Instant
import java.util.UUID

/**
 * A cluster of semantically similar recurring questions surfaced for project managers.
 *
 * A group is the persisted result of an AI grouping run: the AI service clusters raw user
 * questions, and each cluster becomes one [FaqGroup] together with its sample [questions] and the
 * [documents] that answered them. The rows are treated as a rebuildable cache — a refresh replaces
 * the whole set rather than mutating individual groups. [occurrenceCount] is the total number of
 * questions the AI assigned to the cluster and can exceed the number of stored sample [questions].
 */
@Entity
class FaqGroup(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(nullable = false, columnDefinition = "TEXT")
    val question: String,
    @Column(nullable = false)
    val occurrenceCount: Int,
    @Column(nullable = false)
    val refreshedAt: Instant = Instant.now(),
    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    val questions: MutableList<FaqQuestion> = mutableListOf(),
    @OneToMany(mappedBy = "group", cascade = [CascadeType.ALL], orphanRemoval = true)
    val documents: MutableList<FaqDocument> = mutableListOf(),
)
