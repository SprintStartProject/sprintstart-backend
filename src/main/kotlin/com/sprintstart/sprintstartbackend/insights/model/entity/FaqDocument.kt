package com.sprintstart.sprintstartbackend.insights.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import java.util.UUID

/**
 * A knowledge-base document that answered questions in a [FaqGroup].
 *
 * [documentRef] is the identifier of the document in the upstream knowledge base as reported by the
 * AI service. It is exposed to API clients as the document id so they can reference the real
 * document, while [id] remains the internal primary key of this cache row. [source] is optional
 * because the AI service does not always know the document's origin system.
 */
@Entity
class FaqDocument(
    @Id
    val id: UUID = UUID.randomUUID(),
    @Column(name = "document_ref", nullable = false)
    val documentRef: String,
    @Column(nullable = false)
    val title: String,
    val source: String?,
    @ManyToOne(optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    val group: FaqGroup,
)
