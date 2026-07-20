package com.sprintstart.sprintstartbackend.connectors.github

import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.UpdateRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryConfigNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubRepositoryConfigService
import com.sprintstart.sprintstartbackend.connectors.github.service.GithubUpdatesService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Registers and regularly runs jobs within a given schedule for the GitHub connector module.
 */
@Component
class GithubScheduledExecutor(
    private val scheduledExecutor: ScheduledExecutor,
    private val githubRepositoryConfigService: GithubRepositoryConfigService,
    private val githubUpdateService: GithubUpdatesService,
) {
    /**
     * Periodically checks and updates GitHub repositories that are due for synchronization.
     *
     * This method is executed at a fixed rate of 60 seconds and performs the following actions:
     * - Retrieves a list of repositories that are due for synchronization based on the current time.
     * - For each repository:
     *   - Fetches the associated configuration details from the database.
     *   - Launches an asynchronous task to update the repository state using the provided configuration.
     *   - Updates the next synchronization time based on the repository's defined schedule.
     *
     * Repositories with no associated configuration found will result in a
     * [RepositoryConfigNotFoundException] (404) being thrown.
     *
     * If a repository is scheduled for an update, its state will be synchronized via the `updateRepository` service,
     * and the task will be executed asynchronously through the `ScheduledExecutor`.
     */
    @Scheduled(fixedRate = 60_000)
    fun tick() {
        val now = Instant.now()
        val repositoriesToUpdate = githubRepositoryConfigService.findAllRepositoriesDueForSync(now)

        repositoriesToUpdate.forEach { repository ->
            val repoConfig = githubRepositoryConfigService
                .findConfigById(repository.id)
                .orElseThrow { RepositoryConfigNotFoundException(repository.owner, repository.name) }

            scheduledExecutor.launch(
                """
                Updating GitHub repository ${repository.owner}/${repository.name} (auto-update: ${repoConfig.autoUpdate})
            """,
            ) {
                githubUpdateService.updateRepository(
                    UpdateRepositoryRequest(
                        repository.owner,
                        repository.name,
                    ),
                    repoConfig.autoUpdate,
                )
            }

            repoConfig.nextSyncAt = GithubRepositoryConfigService.calculateNextSyncAt(repoConfig.schedule)
            githubRepositoryConfigService.saveRepositoryConfig(repoConfig)
        }
    }
}
