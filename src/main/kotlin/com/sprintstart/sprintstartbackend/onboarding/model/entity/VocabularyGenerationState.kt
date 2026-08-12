package com.sprintstart.sprintstartbackend.onboarding.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

/**
 * What the last vocabulary-generation run saw, so the next one can tell whether anything changed.
 *
 * The AI service is stateless: it short-circuits a run as `unchanged` when the corpus fingerprint it
 * computes matches the one the caller sends, and the caller is the only place that fingerprint can
 * be remembered. Without this row every crawl would re-derive the same vocabulary from the same
 * corpus, paying for a generation to be told nothing is new.
 *
 * ⚠️ **One row, not one per project.** Competencies are global and the generator retrieves across
 * the whole corpus, so there is exactly one thing to remember.
 *
 * ⚠️ The module pass is **not** guarded by this. It has its own per-`(competency, project)`
 * fingerprint, and its real guard is "this competency has no module yet" — a competency left
 * uncovered by an earlier run must still be picked up when the corpus has not moved since.
 */
@Entity
@Table(name = "vocabulary_generation_state")
class VocabularyGenerationState(
    @Id
    val id: UUID = SINGLETON_ID,
    /** The corpus fingerprint the last completed run reported. */
    @Column(name = "last_fingerprint")
    var lastFingerprint: String? = null,
    @Column(name = "last_run_at", nullable = false)
    var lastRunAt: Instant = Instant.now(),
) {
    companion object {
        /**
         * The single row's id.
         *
         * ⚠️ A fixed id rather than "whatever row exists": two crawls finishing at once would each
         * insert their own, and the fingerprint would stop being a single fact about the corpus.
         */
        val SINGLETON_ID: UUID = UUID.fromString("00000000-0000-0000-0000-00000000f1a6")
    }
}
