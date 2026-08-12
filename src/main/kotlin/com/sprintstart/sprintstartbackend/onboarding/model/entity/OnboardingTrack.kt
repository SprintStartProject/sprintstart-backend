package com.sprintstart.sprintstartbackend.onboarding.model.entity

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContributionEvidenceKind
import jakarta.persistence.CollectionTable
import jakarta.persistence.Column
import jakarta.persistence.ElementCollection
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * What onboarding means for one kind of role — the manifest a hire's onboarding is assembled from.
 *
 * A track states, per role, the only two things that differ between a developer and a Scrum
 * Master: **what counts as their work** ([evidenceKinds]) and **what to call it**
 * ([contributionNoun] and friends). The ramp, the metrics and the ledger are shared.
 *
 * ⚠️ **A bundle of defaults, not a cage.** It does not stop a PM who ships code or an engineer who
 * runs the retro from having contributions of both kinds — contributions are counted from one
 * stream regardless of which source produced them.
 *
 * ⚠️ **The vocabulary is structured, never prose.** The nouns are separate fields rendered into
 * fixed slots; a track must never contribute arbitrary prose to the buddy's system prompt.
 *
 * ⚠️ [evidenceKinds] is the single switch behind both "which contributions can this hire have" and
 * "which buddy tools are worth offering them" — a track that cannot have a pull request must not be
 * offered a tool that lists them.
 *
 * ⚠️ An **empty** [evidenceKinds] is a real state, not a misconfiguration: nothing this role does is
 * observable yet, so their work cannot be measured until a source for it exists.
 */
@Entity
@Table(name = "onboarding_tracks")
class OnboardingTrack(
    @Id
    val id: UUID = UUID.randomUUID(),
    // Referenced by stable key rather than id, matching how competencies are referenced
    // everywhere: a role points at a track by key, so renaming a track's label breaks nothing.
    // `key` is a reserved word in several dialects; backticks make Hibernate quote it.
    @Column(name = "`key`", nullable = false, unique = true)
    val key: String,
    @Column(nullable = false)
    var label: String,
    /**
     * What one unit of this role's accepted work is called, bare: "change", "ceremony".
     *
     * Bare because it is always rendered next to [contributionVerbPast] -- "merged change",
     * "facilitated ceremony" -- and baking the verb into the noun produces "merged merged change"
     * the moment a sentence needs both.
     */
    @Column(name = "contribution_noun", nullable = false)
    var contributionNoun: String,
    /** The plural, because "You've merged 3 changes here" cannot be built from the singular. */
    @Column(name = "contribution_noun_plural", nullable = false)
    var contributionNounPlural: String,
    /** How the hire's own act reads in the past tense: "merged", "facilitated", "published". */
    @Column(name = "contribution_verb_past", nullable = false)
    var contributionVerbPast: String,
    @ElementCollection
    @CollectionTable(
        name = "onboarding_track_evidence_kinds",
        joinColumns = [JoinColumn(name = "onboarding_track_id")],
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_kind", nullable = false)
    val evidenceKinds: MutableSet<ContributionEvidenceKind> = mutableSetOf(),
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    /** Whether this role's work can show up as [kind] at all. */
    fun admits(kind: ContributionEvidenceKind): Boolean = kind in evidenceKinds

    companion object {
        /**
         * The track every role falls back to, and the one every existing role was migrated onto.
         *
         * A fallback rather than a hard failure: a project that has not thought about tracks yet
         * must keep onboarding people exactly as it did before, not start throwing.
         */
        const val DEFAULT_KEY = "engineering"
    }
}
