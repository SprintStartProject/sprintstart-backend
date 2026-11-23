package com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.config

import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstanceConfig
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import java.time.Instant

internal data class GetJiraInstanceConfigResponse(
    val instanceUrl: String,
    val autoUpdate: Boolean,
    val spec: ScheduleSpec?,
    val schedule: String,
    val nextSyncAt: Instant?,
) {
    companion object {
        internal fun of(config: JiraInstanceConfig): GetJiraInstanceConfigResponse {
            return GetJiraInstanceConfigResponse(
                instanceUrl = checkNotNull(config.id) { "A stored Jira instance config has its URL as id" },
                autoUpdate = config.autoUpdate,
                spec = config.spec,
                schedule = config.schedule,
                nextSyncAt = config.nextSyncAt,
            )
        }
    }
}
