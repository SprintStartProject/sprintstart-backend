package com.sprintstart.sprintstartbackend.insights.service

import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Tracks which projects have an insight recomputation in progress.
 *
 * Exists so a panel that refreshes itself can say "updating…" instead of showing stale numbers
 * with no explanation — a knowledge-gap scan after an ingestion run takes long enough that a PM
 * would otherwise see the old result and conclude nothing happened.
 *
 * Held in memory, not persisted: it describes what *this* instance is doing right now, and a
 * restart correctly forgets it, since the work died with the process.
 */
@Component
class InsightsRefreshTracker {
    private val knowledgeGapsInProgress = ConcurrentHashMap.newKeySet<UUID>()

    fun markKnowledgeGapsRefreshing(projectId: UUID) {
        knowledgeGapsInProgress.add(projectId)
    }

    fun markKnowledgeGapsIdle(projectId: UUID) {
        knowledgeGapsInProgress.remove(projectId)
    }

    fun isRefreshingKnowledgeGaps(projectId: UUID): Boolean = knowledgeGapsInProgress.contains(projectId)
}
