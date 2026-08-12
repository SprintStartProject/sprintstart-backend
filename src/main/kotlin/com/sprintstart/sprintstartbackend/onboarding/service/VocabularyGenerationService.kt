package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.OnboardingAiClient
import com.sprintstart.sprintstartbackend.onboarding.external.enums.CompetencyKind
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContentProvenance
import com.sprintstart.sprintstartbackend.onboarding.external.model.ActiveCompetencySchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.ProposedCompetencySchema
import com.sprintstart.sprintstartbackend.onboarding.external.model.TombstonedCompetencySchema
import com.sprintstart.sprintstartbackend.onboarding.model.entity.Competency
import com.sprintstart.sprintstartbackend.onboarding.model.entity.VocabularyGenerationState
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyTombstoneRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.VocabularyGenerationStateRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * Derives the competency vocabulary from the corpus, whenever the corpus changes.
 *
 * This is what makes "set up onboarding" collapse into "connect a repository": nobody presses a
 * button, and nobody approves the result. The gate is **grounding, not a click** — a competency that
 * cannot cite a chunk never reaches this service, and one that can is published.
 *
 * ⚠️ **Two things it must never destroy:**
 * - **A `PM` row is never touched.** Somebody corrected it, and regeneration overwriting that would
 *   mean the correction quietly never happened.
 * - **A tombstoned key is never resurrected.** The generator is told about them, but being told is
 *   not the same as being prevented, so the persister refuses them too.
 *
 * ⚠️ Read tx → AI call outside any transaction → write tx. Holding a transaction open across a
 * model call would pin a connection for the length of a generation.
 */
@Service
class VocabularyGenerationService(
    private val competencyRepository: CompetencyRepository,
    private val tombstoneRepository: CompetencyTombstoneRepository,
    private val stateRepository: VocabularyGenerationStateRepository,
    private val onboardingAiClient: OnboardingAiClient,
    private val areaNormalizer: CompetencyAreaNormalizer,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }
    private val txTemplate = TransactionTemplate(transactionManager)

    /**
     * Runs one generation pass over the whole vocabulary.
     *
     * @return How many competencies were added, for logging and for the caller's own record.
     */
    @Tracked("Generating the competency vocabulary from the corpus")
    suspend fun generate(): GenerationSummary {
        val request = withContext(Dispatchers.IO) { readTxTemplate.execute { loadRequest() }!! }

        val outcome = onboardingAiClient.proposeCompetencyGraph(
            activeCompetencies = request.active,
            existingAreas = request.areas,
            tombstonedCompetencies = request.tombstoned,
            lastFingerprint = request.lastFingerprint,
        )

        if (outcome.status != PROPOSED) {
            // `unchanged` is the fingerprint guard doing its job; `skipped` means the corpus could
            // not ground anything. Neither is a failure, and neither writes.
            logger.info(
                "Vocabulary generation returned {}: {}",
                outcome.status,
                outcome.notes.firstOrNull() ?: "no notes",
            )
            return GenerationSummary(status = outcome.status, added = 0, skippedPmRows = 0)
        }

        return withContext(Dispatchers.IO) {
            txTemplate.execute { persist(outcome.competencies, outcome.provenance?.corpusFingerprint) }!!
        }
    }

    private fun loadRequest(): GenerationRequest {
        val live = competencyRepository.findAll()
        return GenerationRequest(
            active = live.map {
                ActiveCompetencySchema(
                    key = it.key,
                    label = it.label,
                    description = it.description.orEmpty(),
                    kind = it.kind.name,
                    area = it.area,
                    repoRef = it.repoRef,
                )
            },
            areas = competencyRepository.findDistinctAreas(),
            tombstoned = tombstoneRepository.findAll().map {
                TombstonedCompetencySchema(key = it.key, label = it.label)
            },
            lastFingerprint = stateRepository
                .findById(VocabularyGenerationState.SINGLETON_ID)
                .orElse(null)
                ?.lastFingerprint,
        )
    }

    private fun persist(proposed: List<ProposedCompetencySchema>, fingerprint: String?): GenerationSummary {
        // Re-read rather than trusting what the request was built from: the AI call happened outside
        // any transaction, so somebody may have authored or removed a competency while it ran.
        val existing = competencyRepository.findAll().associateBy { it.key }
        val tombstoned = tombstoneRepository.findAll().map { it.key }.toSet()

        var added = 0
        var skippedPmRows = 0

        proposed.forEach { candidate ->
            val current = existing[candidate.key]
            when {
                current != null && current.provenance == ContentProvenance.PM -> {
                    // A person's row. Regeneration must leave it exactly as they left it.
                    skippedPmRows++
                }

                candidate.key in tombstoned -> {
                    // The generator was told, and proposed it anyway. It does not get to win.
                    logger.info("Refused to resurrect tombstoned competency {}", candidate.key)
                }

                current != null -> {
                    current.label = candidate.label
                    current.description = candidate.description.takeIf(String::isNotBlank)
                    current.area = areaNormalizer.normalize(candidate.area)
                    current.repoRef = candidate.repoRef
                    current.provenance = ContentProvenance.AI
                }

                else -> {
                    competencyRepository.save(
                        Competency(
                            key = candidate.key,
                            label = candidate.label,
                            description = candidate.description.takeIf(String::isNotBlank),
                            kind = kindOf(candidate.kind),
                            area = areaNormalizer.normalize(candidate.area),
                            repoRef = candidate.repoRef,
                            provenance = ContentProvenance.AI,
                        ),
                    )
                    added++
                }
            }
        }

        rememberFingerprint(fingerprint)

        logger.info(
            "Vocabulary generation added {} competencies, left {} PM-authored rows alone",
            added,
            skippedPmRows,
        )
        return GenerationSummary(status = PROPOSED, added = added, skippedPmRows = skippedPmRows)
    }

    private fun rememberFingerprint(fingerprint: String?) {
        val state = stateRepository
            .findById(VocabularyGenerationState.SINGLETON_ID)
            .orElseGet { VocabularyGenerationState() }
        state.lastFingerprint = fingerprint
        state.lastRunAt = Instant.now()
        stateRepository.save(state)
    }

    /** An unknown kind is a `SKILL`, not a dropped competency: the vocabulary matters, its label does not. */
    private fun kindOf(raw: String): CompetencyKind =
        CompetencyKind.entries.firstOrNull { it.name == raw } ?: CompetencyKind.SKILL

    private data class GenerationRequest(
        val active: List<ActiveCompetencySchema>,
        val areas: List<String>,
        val tombstoned: List<TombstonedCompetencySchema>,
        val lastFingerprint: String?,
    )

    /** What one pass did, for the log and for whoever asked. */
    data class GenerationSummary(
        val status: String,
        val added: Int,
        val skippedPmRows: Int,
    )

    private companion object {
        const val PROPOSED = "proposed"
    }
}
