package com.sprintstart.sprintstartbackend.insights.external

import java.util.UUID

/**
 * Exported insights API: rebuilding a project's derived insight views on request.
 *
 * Other modules depend on this interface rather than on the insights services. Both operations call
 * the AI service and can take a while, which is why they suspend.
 */
interface InsightsRefreshApi {
    /**
     * Regroups every question asked on a project into its recurring-question groups.
     *
     * @param projectId The project whose FAQ to rebuild.
     * @return How many groups were stored.
     */
    suspend fun refreshFaq(projectId: UUID): Int

    /**
     * Rescans a project's components for missing documentation.
     *
     * @param projectId The project to rescan.
     * @return How many components have a gap, and how many the scan covered.
     */
    suspend fun refreshKnowledgeGaps(projectId: UUID): KnowledgeGapsRefresh
}

/**
 * The outcome of a knowledge-gap rescan.
 *
 * @property gapCount Components missing at least one document type.
 * @property componentCount Components the scan covered, including those missing nothing.
 */
data class KnowledgeGapsRefresh(
    val gapCount: Int,
    val componentCount: Int,
)
