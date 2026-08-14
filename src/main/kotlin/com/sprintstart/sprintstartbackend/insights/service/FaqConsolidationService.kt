package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.insights.InsightsAiClient
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqConsolidateCategoriesRequest
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
 * Keeps the FAQ's structure from growing past what a PM can actually read.
 *
 * Two ceilings are enforced, both by folding things together rather than by refusing new entries —
 * a limit must never lose a question:
 *
 * - too many categories: related ones are merged, so the top level stays scannable;
 * - too many groups in one category: duplicates are merged, which is where the incremental
 *   classifier's occasional double-opened group gets cleaned up.
 *
 * Both passes send the AI service only structure — category names and counts, or one category's
 * representative questions — never the question history. That is what makes running them on
 * crossing a ceiling affordable rather than something to schedule nightly.
 *
 * A failing consolidation is logged and swallowed: the question that triggered it is already
 * filed, and an over-full category is a cosmetic problem next to losing the update.
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
     * Applies both ceilings after a question was filed.
     *
     * @param touchedCategory the category the new question landed in, checked for its group
     * ceiling. Categories the question did not touch cannot have grown, so they are left alone.
     */
    @Tracked("Enforcing FAQ structure limits")
    suspend fun enforceLimits(projectId: UUID, touchedCategory: String?) {
        try {
            // Category consolidation first: it can move groups into the touched category, so
            // checking the group ceiling before it would miss the overflow it just caused.
            val remapped = consolidateCategories(projectId)
            val category = remapped[touchedCategory] ?: touchedCategory
            if (category != null) {
                mergeGroupsIn(projectId, category)
            }
        } catch (e: Exception) {
            logger.warn("FAQ consolidation failed for project {}", projectId, e)
        }
    }

    /**
     * Merges related categories when the project has more than the configured ceiling.
     *
     * @return which merged-away category names now point to which surviving one, so a caller
     * holding a category name from before the merge can follow it.
     */
    @Tracked("Consolidating FAQ categories")
    suspend fun consolidateCategories(projectId: UUID): Map<String, String> {
        val groups = readGroups(projectId)
        val categories = FaqSnapshot.categoriesOf(groups)
        if (categories.size <= faqConfig.maxCategories) return emptyMap()

        val plan = insightsAiClient.consolidateFaqCategories(
            AiFaqConsolidateCategoriesRequest(
                categories = categories,
                targetMax = faqConfig.maxCategories,
            ),
        )
        val merges = validMerges(plan.merges, known = categories.map { it.name }.toSet())
        if (merges.isEmpty()) {
            logger.info(
                "Project {} has {} FAQ categories over a ceiling of {}, but nothing was safely mergeable",
                projectId,
                categories.size,
                faqConfig.maxCategories,
            )
            return emptyMap()
        }

        val renames = merges.flatMap { merge -> merge.sources.map { it to merge.into } }.toMap()
        txTemplate.executeWithoutResult {
            faqGroupRepository
                .findAllByProjectIdOrderByOccurrenceCountDesc(projectId)
                .forEach { group ->
                    renames[group.category]?.let { group.category = it }
                }
        }

        logger.info("Consolidated {} FAQ categories into {} for project {}", renames.size, merges.size, projectId)
        return renames
    }

    /**
     * Folds duplicate groups inside one category together when it holds more than the ceiling.
     */
    @Tracked("Merging duplicate FAQ groups")
    suspend fun mergeGroupsIn(projectId: UUID, category: String) {
        val groups = readGroupsIn(projectId, category)
        if (groups.size <= faqConfig.maxGroupsPerCategory) return

        val plan = insightsAiClient.mergeFaqGroups(
            AiFaqMergeGroupsRequest(
                groups = groups.map { it.toAiRef() },
                targetMax = faqConfig.maxGroupsPerCategory,
            ),
        )
        val merges = validMerges(plan.merges, known = groups.map { it.id.toString() }.toSet())
        if (merges.isEmpty()) return

        txTemplate.executeWithoutResult {
            val byId = faqGroupRepository
                .findAllByProjectIdAndCategoryOrderByOccurrenceCountDesc(projectId, category)
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

        logger.info(
            "Merged {} duplicate FAQ group sets in category '{}' of project {}",
            merges.size,
            category,
            projectId,
        )
    }

    /**
     * Folds [source] into [target] and leaves [source] empty, ready to be deleted.
     *
     * The questions and documents are moved rather than copied, and removed from [source]'s own
     * collections as they go: they are mapped with `orphanRemoval`, so a row still listed by the
     * group being deleted would be deleted with it instead of surviving on the target.
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
            // The two groups were duplicates of one question, so they usually cite the same
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
     * so it does not take that on trust: an unknown name, a source claimed twice, or a target that
     * a later merge consumes would each corrupt the result rather than merely degrade it.
     */
    private fun validMerges(merges: List<AiFaqMerge>, known: Set<String>): List<AiFaqMerge> {
        val claimed = mutableSetOf<String>()
        val accepted = mutableListOf<AiFaqMerge>()

        for (merge in merges) {
            if (merge.into.isBlank() || merge.into in claimed) continue
            val sources = merge.sources
                .distinct()
                .filter { it in known && it != merge.into && it !in claimed }
            if (sources.isEmpty()) continue

            claimed.addAll(sources)
            accepted.add(merge.copy(sources = sources))
        }

        return accepted.filterNot { it.into in claimed }
    }

    private fun readGroups(projectId: UUID): List<FaqGroup> =
        txTemplate
            .execute {
                faqGroupRepository.findAllByProjectIdOrderByOccurrenceCountDesc(projectId)
            }.orEmpty()

    private fun readGroupsIn(projectId: UUID, category: String): List<FaqGroup> =
        txTemplate
            .execute {
                faqGroupRepository.findAllByProjectIdAndCategoryOrderByOccurrenceCountDesc(projectId, category)
            }.orEmpty()
}
