package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContentProvenance
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
     * What this competency is *about* — "Authentication", "Ingestion" — used to group the vocabulary.
     *
     * This is what replaced the graph. `RELATED` edges were already reaching for exactly this
     * ("part of, commonly used together, same area of the system") and storing it as a DAG, where
     * every consumer filtered them out. Grouping answers the question a learning area actually gets
     * asked — *"what else is about auth?"* — which ordering never did.
     *
     * Free text rather than an enum: a fixed taxonomy cannot fit an unknown codebase, and any list
     * we picked would be engineering-shaped, which is the gap that already leaves non-engineering
     * roles with an empty vocabulary. Fragmentation is held off at the *write*
     * ([CompetencyAreaNormalizer]) rather than by the type — an area that differs only in case or
     * spacing from one already in use is stored as the one already in use.
     *
     * Null is a real state: "not grouped yet".
     */
    @Column(nullable = true)
    var area: String? = null,
    /**
     * Who last had a hand in this competency: the generator, or a person.
     *
     * Mirrors [com.sprintstart.sprintstartbackend.onboarding.model.entity.ModulePage]'s provenance,
     * for the same reason and with the same rule: **any edit marks it `PM`**, and regeneration must
     * leave a `PM` row alone. Without it, "generation runs on ingestion and an admin can correct it"
     * silently means "generation overwrites the admin", and the correction is lost with no trace
     * that it was ever made.
     *
     * ⚠️ Defaults to `PM`, which is the safe direction to be wrong in: if a persister forgets to
     * mark what it wrote as `AI`, the failure is that a row never improves, rather than that
     * somebody's correction is silently discarded.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var provenance: ContentProvenance = ContentProvenance.PM,
    /**
     * The proficiency rank (1..4) a user must reach for this node to count as met.
     *
     * Node levels are *target-level and binary*: a node is met or it isn't, and this is the bar.
     * Without it the projection had nothing to compare a ledger entry against and treated any
     * non-zero level as mastery -- so a hire assessed as `beginner` was shown as done.
     *
     * Defaults to [DEFAULT_TARGET_LEVEL] (intermediate) because `beginner` is the level the
     * interviewer records when it has the least to go on; a bar of 1 would make every such
     * placement instantly master the node, which is the bug this column exists to fix. PMs author
     * real per-node bars via graph editing.
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
