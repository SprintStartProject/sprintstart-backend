package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * A competency somebody deliberately removed, remembered so the generator cannot bring it back.
 *
 * ⚠️ Dedup matches on the exact key **and** on embedding similarity, so without this a removed
 * competency returns on the next crawl under a rephrasing that the key check misses.
 *
 * ⚠️ **A table rather than a flag on `Competency`**: a flag needs every existing reader to filter
 * it out, and any reader added later that forgets creates a competency that is deleted but still
 * visible. **No reader can forget a table it does not query.** Removal is a real delete; this
 * remembers that it happened.
 *
 * ⚠️ It carries the **label**, not just the key, because what it must block is a *rephrasing* — the
 * label is what the similarity check embeds, and a tombstone the generator never sees is not a
 * tombstone.
 *
 * ⚠️ Hand-authoring the same key again clears the tombstone: the rule binds the generator, not the
 * person who changed their mind.
 */
@Entity
@Table(name = "competency_tombstones")
class CompetencyTombstone(
    @Id
    val id: UUID = UUID.randomUUID(),
    // `key` is a reserved word in several dialects (e.g. H2); backticks tell Hibernate to
    // emit a dialect-appropriate quoted identifier.
    @Column(name = "`key`", nullable = false, unique = true)
    val key: String,
    /** What it was called, so a re-proposal can be recognised by meaning and not only by key. */
    @Column(nullable = false)
    var label: String,
    @Column(name = "deleted_at", nullable = false)
    var deletedAt: Instant = Instant.now(),
)
