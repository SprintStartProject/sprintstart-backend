package com.sprintstart.sprintstartbackend.insights.assistant

import com.sprintstart.sprintstartbackend.assistant.external.TeamToolContext
import com.sprintstart.sprintstartbackend.insights.external.InsightsRefreshApi
import kotlinx.serialization.json.JsonObject
import org.springframework.stereotype.Component

/** Offers to rebuild the project's recurring-question groups. */
@Component
class RefreshFaqAction(
    private val insightsRefreshApi: InsightsRefreshApi,
) : RefreshAction(
        name = "refresh_faq",
        description = "Offer to rebuild this project's FAQ: every question asked here regrouped into recurring " +
            "questions. It asks the AI service and takes a while. Offer it when the manager wants the FAQ up to " +
            "date, not as a way to answer a question. Takes no arguments.",
        label = "Refresh the FAQ",
        preview = "Rebuild this project's FAQ: every question asked here is regrouped into recurring " +
            "questions, replacing the current groups. It asks the AI service and can take a while.",
    ) {
    override suspend fun perform(params: JsonObject, context: TeamToolContext): String {
        val groups = insightsRefreshApi.refreshFaq(context.projectId)
        return "Refreshed the FAQ: $groups recurring-question groups."
    }
}
