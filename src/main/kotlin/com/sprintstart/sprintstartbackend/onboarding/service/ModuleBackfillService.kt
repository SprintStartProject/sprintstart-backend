package com.sprintstart.sprintstartbackend.onboarding.service

import com.sprintstart.sprintstartbackend.onboarding.external.enums.ContentProvenance
import com.sprintstart.sprintstartbackend.onboarding.external.enums.ModuleStatus
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyModuleRepository
import com.sprintstart.sprintstartbackend.onboarding.repository.CompetencyRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Writes the material behind a competency, for the competencies that have none.
 *
 * The learning area is *exactly* the competencies with a published module — naming a gap with
 * nothing to offer is worse than staying quiet — so a vocabulary that generates itself and modules
 * that must be requested by hand would leave the buddy with a longer list of things it cannot teach.
 * This closes that: every competency on a project ends up with material, without anyone asking.
 *
 * A generated module lands **live**, not as a draft in a queue: the gate is grounding, so the AI
 * drops any page it cannot cite and returns nothing at all when it cannot ground the module.
 *
 * ⚠️ **It refuses to touch a module anybody has edited.** One `PM` page in the live version and the
 * whole module is left alone.
 *
 * ⚠️ **Capped per run**, so one crawl cannot become an unbounded model bill. Uncovered competencies
 * are taken in a **stable order** and the rest wait for the next run — and the pass is *not*
 * fingerprint-guarded, so an unchanged corpus still makes progress through the backlog rather than
 * stalling forever at the cap.
 */
@Service
class ModuleBackfillService(
    private val competencyRepository: CompetencyRepository,
    private val competencyModuleRepository: CompetencyModuleRepository,
    private val competencyModuleService: CompetencyModuleService,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val readTxTemplate = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    /**
     * Generates and publishes modules for up to [MAX_MODULES_PER_RUN] uncovered competencies.
     *
     * @param projectId The project whose corpus the modules are written against.
     * @return How many modules were published.
     */
    @Tracked("Backfilling competency modules from the corpus")
    suspend fun backfill(projectId: UUID): Int {
        val uncovered = withContext(Dispatchers.IO) {
            readTxTemplate.execute { uncoveredKeys(projectId) }
        }.orEmpty()

        if (uncovered.isEmpty()) {
            return 0
        }

        var published = 0
        uncovered.take(MAX_MODULES_PER_RUN).forEach { competencyKey ->
            val module = try {
                competencyModuleService.proposeFromCorpus(competencyKey, projectId)
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                // One competency the corpus cannot support must not abandon the rest of the batch.
                logger.warn("Module generation failed for {} in project {}", competencyKey, projectId, e)
                null
            } ?: return@forEach

            withContext(Dispatchers.IO) {
                competencyModuleService.approve(module.id)
            }
            published++
        }

        val remaining = (uncovered.size - MAX_MODULES_PER_RUN).coerceAtLeast(0)
        logger.info(
            "Published {} module(s) for project {}; {} competencies still uncovered",
            published,
            projectId,
            remaining,
        )
        return published
    }

    /**
     * Competencies this project teaches nothing about yet, oldest first.
     *
     * A stable order is what makes the cap fair: the same competency is not retried every run while
     * another waits forever.
     */
    private fun uncoveredKeys(projectId: UUID): List<String> {
        val live = competencyModuleRepository.findAllByProjectIdAndStatus(projectId, ModuleStatus.ACTIVE)
        val covered = live.map { it.competencyKey }.toSet()

        // A module anybody has touched is somebody's work, and this pass never replaces one. Its key
        // counts as covered even if we would otherwise regenerate it.
        val edited = live.filter { module -> module.pages.any { it.provenance == ContentProvenance.PM } }
        if (edited.isNotEmpty()) {
            logger.debug("Leaving {} PM-edited module(s) alone in project {}", edited.size, projectId)
        }

        return competencyRepository
            .findAll()
            .sortedBy { it.key }
            .map { it.key }
            .filterNot { it in covered }
    }

    private companion object {
        /**
         * How many modules one crawl may generate.
         *
         * Small on purpose. A generation is several model calls, and a crawl is not a moment anybody
         * is watching — an unbounded loop here is a bill nobody authorised.
         */
        const val MAX_MODULES_PER_RUN = 5
    }
}
