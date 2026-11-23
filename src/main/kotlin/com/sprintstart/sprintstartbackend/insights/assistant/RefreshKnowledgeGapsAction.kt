package com.sprintstart.sprintstartbackend.insights.assistant

import com.sprintstart.sprintstartbackend.assistant.external.TeamToolContext
import com.sprintstart.sprintstartbackend.insights.external.InsightsRefreshApi
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Component

/** Offers to rescan the project's components for missing documentation. */
@Component
class RefreshKnowledgeGapsAction(
    private val insightsRefreshApi: InsightsRefreshApi,
) : RefreshAction(
        name = "refresh_knowledge_gaps",
        description = "Offer to rescan this project's components for missing documentation. It asks the AI " +
            "service and takes a while. Takes no arguments.",
        label = "Rescan for documentation gaps",
        preview = "Rescan this project's components for missing documentation, replacing the current list " +
            "of gaps. It asks the AI service and can take a while.",
    ) {
    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val refresh = insightsRefreshApi.refreshKnowledgeGaps(context.projectId)
        return "Rescanned ${refresh.componentCount} components: ${refresh.gapCount} are missing documentation."
    }
}
