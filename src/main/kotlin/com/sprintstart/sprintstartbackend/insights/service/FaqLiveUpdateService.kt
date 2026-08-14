package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.chat.external.events.ChatQuestionAskedEvent
import com.sprintstart.sprintstartbackend.insights.InsightsAiClient
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqClassifyRequest
import com.sprintstart.sprintstartbackend.insights.model.ai.AiFaqClassifyResponse
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqDocument
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqGroup
import com.sprintstart.sprintstartbackend.insights.model.entity.FaqQuestion
import com.sprintstart.sprintstartbackend.insights.repository.FaqGroupRepository
import com.sprintstart.sprintstartbackend.insights.repository.FaqQuestionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the FAQ insight current as questions are asked, instead of only when a PM presses refresh.
 *
 * Every question asked to the AI Buddy is filed into the existing structure by the AI service:
 * dropped if it is smalltalk, otherwise added to the group it repeats or opened as a new one under
 * a category. That is a single small AI call whose cost does not grow with the project's question
 * history, which is what makes it affordable per message — the full rebuild in
 * [InsightsFaqService] costs one pass over every question ever asked and stays the manual
 * fallback.
 *
 * The trade-off is accepted deliberately: one question carries little context, so the classifier
 * will occasionally open a group that duplicates an existing one. Folding those back together once
 * a ceiling is crossed (see [FaqConsolidationService]) is far cheaper than comparing against the
 * whole corpus every time, and bounds the drift instead of letting it accumulate.
 */
@Service
class FaqLiveUpdateService(
    private val faqGroupRepository: FaqGroupRepository,
    private val faqQuestionRepository: FaqQuestionRepository,
    private val insightsAiClient: InsightsAiClient,
    private val faqConsolidationService: FaqConsolidationService,
    private val applicationConfig: ApplicationConfig,
    transactionManager: PlatformTransactionManager,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // @Transactional cannot be used here, the methods are suspend.
    private val txTemplate = TransactionTemplate(transactionManager)

    // Two questions asked at the same time in one project would otherwise be classified against
    // the same snapshot and could each open a group for the same topic, or both trip a ceiling and
    // run two consolidations over the same rows. Per project rather than global, so one busy
    // project cannot stall another's updates.
    private val projectLocks = ConcurrentHashMap<UUID, Mutex>()

    private val faqConfig get() = applicationConfig.insights.faq

    /**
     * Files a freshly asked question into the project's FAQ.
     *
     * Silently does nothing when live updates are switched off, when the question was already
     * filed, or when the AI service classifies it as not a real question.
     */
    @Tracked("Filing an asked question into the FAQ")
    suspend fun onQuestionAsked(event: ChatQuestionAskedEvent) {
        if (!faqConfig.liveUpdates) return

        projectLocks.computeIfAbsent(event.projectId) { Mutex() }.withLock {
            // Events can be redelivered; counting the same message twice would inflate whichever
            // group it lands in and quietly corrupt the frequency ranking the whole panel is
            // ordered by.
            if (faqQuestionRepository.existsBySourceMessageId(event.messageId)) {
                logger.debug("Question from message {} is already filed, skipping", event.messageId)
                return
            }

            val groups = readGroups(event.projectId)
            val classification = insightsAiClient.classifyFaqQuestion(
                AiFaqClassifyRequest(
                    projectId = event.projectId.toString(),
                    question = event.question,
                    categories = FaqSnapshot.categoriesOf(groups),
                    groups = FaqSnapshot.candidatesOf(groups, faqConfig.candidateGroups),
                ),
            )

            if (!classification.relevant) {
                logger.debug("Question from message {} is not a documentation question", event.messageId)
                return
            }

            val category = apply(event, classification, groups)
            faqConsolidationService.enforceLimits(event.projectId, category)
        }
    }

    private fun readGroups(projectId: UUID): List<FaqGroup> =
        txTemplate
            .execute {
                faqGroupRepository.findAllByProjectIdOrderByOccurrenceCountDesc(projectId)
            }.orEmpty()

    /**
     * Persists the classification and returns the category the question ended up in.
     */
    private fun apply(
        event: ChatQuestionAskedEvent,
        classification: AiFaqClassifyResponse,
        groups: List<FaqGroup>,
    ): String? {
        val category = resolveCategory(classification.category, groups)
        val matchedId = classification.groupId?.let(::parseUuidOrNull)

        return txTemplate.execute {
            // Re-read rather than reuse the snapshot: it was loaded outside this transaction and
            // before an AI call that can take seconds, so its entities are detached and its counts
            // may already be stale.
            val matched = matchedId
                ?.let { faqGroupRepository.findByIdAndProjectId(it, event.projectId).orElse(null) }

            val group = matched ?: newGroup(event, classification, category)
            if (matched != null) {
                matched.occurrenceCount += 1
                matched.refreshedAt = Instant.now()
                // Guarded rather than assigned: a redelivered or delayed event can carry a time
                // older than the group's newest question, and recency must not go backwards.
                if (event.askedAt.isAfter(matched.lastAskedAt)) matched.lastAskedAt = event.askedAt
            }

            group.questions.add(
                FaqQuestion(
                    text = classification.question.ifBlank { event.question },
                    askedAt = event.askedAt,
                    sourceMessageId = event.messageId,
                    group = group,
                ),
            )

            faqGroupRepository.save(group)
            group.category
        }
    }

    private fun newGroup(
        event: ChatQuestionAskedEvent,
        classification: AiFaqClassifyResponse,
        category: String?,
    ): FaqGroup {
        val group = FaqGroup(
            projectId = event.projectId,
            question = classification.question.ifBlank { event.question },
            occurrenceCount = 1,
            category = category,
            firstAskedAt = event.askedAt,
            lastAskedAt = event.askedAt,
        )
        classification.documents.forEach { document ->
            group.documents.add(
                FaqDocument(
                    documentRef = document.id,
                    title = document.title,
                    source = document.source,
                    group = group,
                ),
            )
        }
        return group
    }

    /**
     * Snaps a classified category onto the spelling the project already uses.
     *
     * The AI service is asked to copy existing names exactly and usually does, but a single
     * case or spacing difference would open a second bucket that reads to a PM as the same topic
     * listed twice. Cheap to guard here, impossible to notice later.
     */
    private fun resolveCategory(raw: String?, groups: List<FaqGroup>): String? {
        val label = raw?.trim()?.replace(WHITESPACE, " ")
        if (label.isNullOrBlank()) return null
        return groups
            .mapNotNull { it.category }
            .firstOrNull { it.equals(label, ignoreCase = true) }
            ?: label
    }

    private companion object {
        private val WHITESPACE = Regex("\\s+")
    }
}

/**
 * Parses [value] as a [UUID], or null if it isn't one.
 *
 * The AI service can return an id it invented rather than one of the candidates it was given. That
 * should open a new group, which is what a null match produces — not kill the classification.
 */
internal fun parseUuidOrNull(value: String): UUID? =
    try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
    }
