package com.sprintstart.sprintstartbackend.connectors.jira

import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraInstanceConfigService
import com.sprintstart.sprintstartbackend.connectors.jira.service.JiraUpdateService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Component responsible for managing periodic synchronization tasks for Jira instance configurations.
 */
@Component
internal class JiraScheduledExecutor(
    private val scheduledExecutor: ScheduledExecutor,
    private val configService: JiraInstanceConfigService,
    private val updateService: JiraUpdateService,
) {
    @Scheduled(fixedRate = 60_000)
    fun tick() {
        val now = Instant.now()
        val instancesDueForSync = configService.findAllJiraInstanceConfigsDueForSync(now)

        instancesDueForSync.forEach { config ->
            scheduledExecutor.launch("Updating Jira instance '${config.id}' (auto-update: ${config.autoUpdate})") {
                updateService.updateJiraInstance(config.instance.instanceUrl, config.autoUpdate)
            }

            config.nextSyncAt = configService.calculateNextSyncAt(config.schedule)
            configService.saveJiraInstanceConfig(config)
        }
    }
}
