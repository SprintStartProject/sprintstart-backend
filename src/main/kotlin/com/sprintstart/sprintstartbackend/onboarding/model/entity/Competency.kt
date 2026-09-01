package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A node in the onboarding competency graph — one durable thing a hire can be proficient in.
 *
 * Competencies are referenced everywhere by their stable [key], never by [id]: progress in the
 * ledger and edges in the graph point at the key so they survive renames and re-seeding. The
 * [id] exists only as the JPA primary key. Traversal and gap logic live in Phase 2; this entity
 * is just the persisted node the placement writes to and the path projects from.
 */
@Entity
@Table(name = "competencies")
class Competency(
    @Id
    val id: UUID = UUID.randomUUID(),
    // `key` is a reserved word in several dialects (e.g. H2); backticks tell Hibernate to
    // emit a dialect-appropriate quoted identifier.
    @Column(name = "`key`", nullable = false, unique = true)
    val key: String,
    @Column(nullable = false)
    var label: String,
    @Column(nullable = true, columnDefinition = "TEXT")
    var description: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var kind: CompetencyKind,
    // Optional pointer to the repository artifact this competency is grounded in (e.g. a path or module).
    @Column(nullable = true)
    var repoRef: String? = null,
    /**
     * The proficiency rank (1..4) a user must reach for this node to count as met.
     *
     * Node levels are *target-level and binary*: a node is met or it isn't, and this is the bar.
     * The projection compares a ledger entry against this bar; without one it has nothing to
     * measure against and any non-zero level reads as mastery, showing a hire assessed as
     * `beginner` as done.
     *
     * Defaults to [DEFAULT_TARGET_LEVEL] (intermediate) because `beginner` is the level the
     * interviewer records when it has the least to go on, and a bar of 1 would make every such
     * placement instantly master the node. PMs author real per-node bars via graph editing.
     */
    @Column(name = "target_level", nullable = false)
    var targetLevel: Int = DEFAULT_TARGET_LEVEL,
    @Column(nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    companion object {
        /**
         * The bar a competency gets until a PM authors one: intermediate (rank 2).
         *
         * Deliberately above `beginner`, which is what the interviewer records when it has the
         * least evidence to go on.
         */
        const val DEFAULT_TARGET_LEVEL = 2
    }
}
