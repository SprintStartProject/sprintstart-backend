@file:Suppress("ktlint:standard:kdoc")

/**package com.sprintstart.sprintstartbackend.github

import com.sprintstart.sprintstartbackend.github.service.GithubConnectorService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * Registers and regularly runs jobs within a given schedule for the GitHub connector module.
 */
@Component
class GithubScheduledExecutor(
 private val githubConnectorService: GithubConnectorService,
 private val scheduledExecutor: ScheduledExecutor,
) {
 /**
 * Registers a scheduled job to regularly check for updates in all connected
 * GitHub repositories.
 */
 @Scheduled(cron = $$"${sprintstart.github.cron:0 0 2 * * *}")
 fun syncAllRepositories() {
 scheduledExecutor.launch("github-sync-repositories") {
 githubConnectorService.updateAllRepositories()
 }
 }
}
*/
