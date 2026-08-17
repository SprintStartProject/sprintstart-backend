package com.sprintstart.sprintstartbackend.insights.service

import com.sprintstart.sprintstartbackend.ApplicationConfig
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Rescans a project's knowledge gaps once newly ingested documentation is searchable.
 *
 * The gaps are derived entirely from what the AI service has indexed, so they go stale the moment
 * a repository is ingested — and a PM has no way of knowing that from looking at the panel. This
 * closes that gap without them having to press anything.
 *
 * Two properties matter more than promptness here:
 *
 * - **It waits for indexing, not for ingestion.** A scan started when the run merely *finished*
 *   would query the AI service before the new documents are in its corpus and dutifully report
 *   the gaps the ingestion just closed. Hence [com.sprintstart.sprintstartbackend.ingestion.external.events.ArtifactsIndexedEvent]
 *   rather than the earlier run-finished signal.
 * - **It coalesces.** Connecting a repository produces a burst of runs (files, commits, issues,
 *   pull requests), and each one arriving would trigger its own full scan over the same corpus.
 *   Every event restarts a short timer instead, so the burst costs one scan — the last one, over
 *   the complete result.
 */
@Service
class KnowledgeGapsAutoRefreshService(
    private val knowledgeGapsService: KnowledgeGapsService,
    private val refreshTracker: InsightsRefreshTracker,
    private val applicationConfig: ApplicationConfig,
    private val applicationScope: CoroutineScope,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val pending = ConcurrentHashMap<UUID, Job>()

    private val config get() = applicationConfig.insights.knowledgeGaps

    /**
     * Schedules a rescan of [projectId], restarting the debounce window if one is already waiting.
     */
    @Tracked("Scheduling a knowledge-gaps rescan")
    fun scheduleRefresh(projectId: UUID) {
        if (!config.autoRefresh) return

        refreshTracker.markKnowledgeGapsRefreshing(projectId)
        val job = applicationScope.launch {
            delay(config.debounceSeconds.seconds)
            // Only clear the slot if it is still ours: a later event may have replaced this job
            // while it slept, and removing that one would let its cancellation go unnoticed.
            pending.remove(projectId, coroutineContext[Job])
            refresh(projectId)
        }
        pending.put(projectId, job)?.cancel()
    }

    private suspend fun refresh(projectId: UUID) {
        try {
            val result = knowledgeGapsService.refreshKnowledgeGaps(projectId)
            logger.info(
                "Rescanned knowledge gaps for project {} after ingestion, {} of {} components have gaps",
                projectId,
                result.gapCount,
                result.componentCount,
            )
        } catch (e: Exception) {
            // A failed rescan leaves the previous gaps in place, which is the right outcome:
            // the panel keeps showing the last known truth rather than emptying out.
            logger.warn("Automatic knowledge-gaps rescan failed for project {}", projectId, e)
        } finally {
            refreshTracker.markKnowledgeGapsIdle(projectId)
        }
    }
}
