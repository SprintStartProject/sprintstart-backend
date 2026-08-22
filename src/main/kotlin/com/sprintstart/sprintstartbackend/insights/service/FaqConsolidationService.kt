package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.insights.InsightsAiClient
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqMerge
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqMergeGroupsRequest
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import com.sprintstart.sprintstartbackend.insights.repository.FaqGroupRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.util.UUID

/**
 * Keeps the FAQ from growing past what a PM can actually read.
 *
 * One ceiling, enforced by folding entries together rather than by refusing new ones — a limit must
 * never lose a question. This is where the incremental classifier's occasional double-opened entry
 * gets cleaned up: filing a single question judges it against a bounded candidate list, so a
 * duplicate is possible, and folding duplicates back together once the ceiling is reached is far
 * cheaper than comparing every question against the whole corpus.
 *
 * The merge pass sends the AI service only structure — each entry's title, representative question
 * and count — never the phrasings stored underneath. That is what makes running it on crossing the
 * ceiling affordable rather than something to schedule nightly.
 *
 * A failing pass is logged and swallowed: the question that triggered it is already filed, and an
 * over-full list is a cosmetic problem next to losing the update.
 */
@Service
class FaqConsolidationService(
    private val faqGroupRepository: FaqGroupRepository,
    private val insightsAiClient: InsightsAiClient,
    private val applicationConfig: ApplicationConfig,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // @Transactional cannot be used here, the methods are suspend.
    private val txTemplate = TransactionTemplate(transactionManager)

    private val faqConfig get() = applicationConfig.insights.faq

    /**
     * Applies the entry ceiling after a question was filed, swallowing any failure.
     */
    @Tracked("Enforcing the FAQ entry limit")
    suspend fun enforceGroupLimit(projectId: UUID) {
        try {
            mergeDuplicates(projectId)
        } catch (e: Exception) {
            logger.warn("FAQ consolidation failed for project {}", projectId, e)
        }
    }

    /**
     * Folds duplicate entries together when the project holds more than the ceiling.
     */
    @Tracked("Merging duplicate FAQ entries")
    suspend fun mergeDuplicates(projectId: UUID) {
        val groups = readGroups(projectId)
        if (groups.size <= faqConfig.maxGroups) return

        val plan = insightsAiClient.mergeFaqGroups(
            AiFaqMergeGroupsRequest(
                groups = groups.map { it.toAiRef() },
                targetMax = faqConfig.maxGroups,
            ),
        )
        val merges = validMerges(plan.merges, known = groups.mapTo(mutableSetOf()) { it.id.toString() })
        if (merges.isEmpty()) {
            logger.info(
                "Project {} has {} FAQ entries over a ceiling of {}, but nothing was safely mergeable",
                projectId,
                groups.size,
                faqConfig.maxGroups,
            )
            return
        }

        txTemplate.executeWithoutResult {
            val byId = faqGroupRepository
                .findAllByProjectIdOrderByOccurrenceCountDesc(projectId)
                .associateBy { it.id.toString() }

            merges.forEach { merge ->
                val target = byId[merge.into] ?: return@forEach
                val sources = merge.sources.mapNotNull { byId[it] }
                if (sources.isEmpty()) return@forEach

                sources.forEach { source -> absorb(target, source) }
                faqGroupRepository.save(target)
                faqGroupRepository.deleteAll(sources)
            }
        }

        logger.info("Merged {} duplicate FAQ entry sets in project {}", merges.size, projectId)
    }

    /**
     * Folds [source] into [target] and leaves [source] empty, ready to be deleted.
     *
     * The questions and documents are moved rather than copied, and removed from [source]'s own
     * collections as they go: they are mapped with `orphanRemoval`, so a row still listed by the
     * entry being deleted would be deleted with it instead of surviving on the target.
     *
     * The target keeps its own title and representative question — it is the entry users actually
     * phrase the question in, which is why the AI service is asked to make the most-asked one the
     * survivor.
     */
    private fun absorb(target: FaqGroup, source: FaqGroup) {
        target.occurrenceCount += source.occurrenceCount
        if (source.firstAskedAt.isBefore(target.firstAskedAt)) target.firstAskedAt = source.firstAskedAt
        if (source.lastAskedAt.isAfter(target.lastAskedAt)) target.lastAskedAt = source.lastAskedAt

        source.questions.toList().forEach { question ->
            source.questions.remove(question)
            question.group = target
            target.questions.add(question)
        }

        val knownDocuments = target.documents.mapTo(mutableSetOf()) { it.documentRef }
        source.documents.toList().forEach { document ->
            source.documents.remove(document)
            // The two entries were duplicates of one question, so they usually cite the same
            // documents; keeping both copies would show a PM the same source twice.
            if (knownDocuments.add(document.documentRef)) {
                document.group = target
                target.documents.add(document)
            }
        }
    }

    /**
     * Drops merges that cannot be applied safely.
     *
     * The AI service validates its own plan, but this module is the one applying it destructively,
     * so it does not take that on trust: an unknown id, a source claimed twice, or a target that a
     * later merge consumes would each corrupt the result rather than merely degrade it.
     *
     * [claimed] holds accepted targets as well as sources, so an id takes part in at most one merge.
     * That is what makes the plan order-independent: a later merge can no longer name an earlier
     * target as its source, because the id is already spoken for when its sources are built.
     */
    private fun validMerges(merges: List<AiFaqMerge>, known: Set<String>): List<AiFaqMerge> {
        val claimed = mutableSetOf<String>()
        val accepted = mutableListOf<AiFaqMerge>()

        for (merge in merges) {
            if (merge.into !in known || merge.into in claimed) continue
            val sources = merge.sources
                .distinct()
                .filter { it in known && it != merge.into && it !in claimed }
            if (sources.isEmpty()) continue

            claimed.addAll(sources)
            claimed.add(merge.into)
            accepted.add(merge.copy(sources = sources))
        }

        return accepted
    }

    private fun readGroups(projectId: UUID): List<FaqGroup> =
        txTemplate
            .execute {
                faqGroupRepository.findAllByProjectIdOrderByOccurrenceCountDesc(projectId)
            }.orEmpty()
}
