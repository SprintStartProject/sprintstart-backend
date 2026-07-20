package com.sprintstart.sprintstartbackend.connectors.github.service

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConnection
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.ConfigureRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.requests.GetRepositoryConfigRequest
import com.sprintstart.sprintstartbackend.connectors.github.models.api.responses.GetRepositoryConfigResponse
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryConfigNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConfigRepository
import com.sprintstart.sprintstartbackend.connectors.github.repository.GithubRepositoryConnectionRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZonedDateTime
import java.util.Optional
import java.util.UUID

@Service
class GithubRepositoryConfigService(
    private val configRepository: GithubRepositoryConfigRepository,
    private val githubRepoRepository: GithubRepositoryConnectionRepository,
    private val cronBuilder: CronBuilder,
) {
    companion object {
        /**
         * Calculates the next synchronization time based on the provided cron schedule.
         *
         * @param schedule A string representing the cron expression that defines the schedule of synchronization.
         * @return The next synchronization time as an [Instant], or null if the calculation fails.
         */
        fun calculateNextSyncAt(schedule: String): Instant? =
            runCatching {
                val cron = CronExpression.parse(schedule)
                cron.next(ZonedDateTime.now())?.toInstant()
            }.getOrNull()
    }

    /**
     * Retrieves a list of all GitHub repository configurations.
     *
     * @return a list containing all GitHub repository configurations.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all GitHub repository configs")
    fun getAll(): List<GetRepositoryConfigResponse> =
        configRepository.findAll().map { GetRepositoryConfigResponse.of(it) }

    /**
     * Configures global settings for all GitHub repositories stored in the configuration repository.
     *
     * This method updates the configuration of all repositories with the specified settings,
     * including the update schedule and whether automatic updates are enabled. It also calculates
     * the next synchronization time for each repository based on the provided schedule.
     *
     * @param request A [ConfigureRepositoryRequest] containing the global configuration settings to apply.
     *                This includes the update schedule and a flag indicating whether automatic updates
     *                should be enabled.
     */
    @Tracked("Configuring all GitHub repositories")
    fun configureAll(request: ConfigureRepositoryRequest) {
        val configs = configRepository.findAll()

        configs.forEach {
            it.autoUpdate = request.autoUpdate
            it.spec = request.schedule
            it.schedule = cronBuilder.build(request.schedule)
            it.nextSyncAt = calculateNextSyncAt(it.schedule)
        }

        configRepository.saveAll(configs)
    }

    /**
     * Retrieves the configuration of a specific GitHub repository based on the provided owner and repository name.
     *
     * @param request A [GetRepositoryConfigRequest] object containing the owner and name of the GitHub repository
     *                for which the configuration is to be retrieved.
     * @return A [GetRepositoryConfigResponse] object containing the configuration details of the specified repository.
     * @throws RepositoryNotConnectedException (400) If the specified repository is not connected.
     * @throws RepositoryConfigNotFoundException (404) If the configuration for the specified repository is not found.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving config of GitHub repository")
    fun getConfigOfRepository(request: GetRepositoryConfigRequest): GetRepositoryConfigResponse {
        val config = findConfigByRepositoryOwnerAndName(request.owner, request.name)
        return GetRepositoryConfigResponse.of(config)
    }

    /**
     * Configures the settings of a specific GitHub repository.
     *
     * This method modifies the repository configuration by updating the auto-update flag,
     * the synchronization schedule, and calculates the next synchronization time.
     *
     * @param owner The owner of the GitHub repository.
     * @param name The name of the GitHub repository.
     * @param request A [ConfigureRepositoryRequest] containing the desired configuration details,
     *                such as the auto-update flag and synchronization schedule.
     */
    @Tracked("Configuring GitHub repository")
    fun configure(owner: String, name: String, request: ConfigureRepositoryRequest) {
        val config = findConfigByRepositoryOwnerAndName(owner, name)

        config.autoUpdate = request.autoUpdate
        config.spec = request.schedule
        config.schedule = cronBuilder.build(request.schedule)
        config.nextSyncAt = calculateNextSyncAt(config.schedule)

        configRepository.save(config)
    }

    /**
     * Retrieves all GitHub repositories that are due for synchronization at the specified time.
     *
     * This method identifies repositories with configurations indicating that they require a sync
     * based on the current or provided time and retrieves their connection details.
     *
     * @param now The current or reference time as an [Instant] against which to determine
     *            whether repositories are due for synchronization.
     * @return A list of [GithubRepositoryConnection] objects representing the GitHub repositories
     *         that are due for synchronization. If no repositories are due, an empty list is returned.
     * @throws RepositoryNotFoundException (404) If a repository's connection cannot be found in the
     *         database during the retrieval process.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all GitHub repositories due for sync now")
    fun findAllRepositoriesDueForSync(now: Instant): List<GithubRepositoryConnection> {
        val dueConfigs = configRepository.findAllDue(now)

        return dueConfigs.map {
            githubRepoRepository.findById(it.id!!).orElseThrow {
                RepositoryNotFoundException("", "", "Repository with id ${it.id} not found")
            }
        }
    }

    /**
     * Retrieves the configuration of a GitHub repository by its unique identifier.
     *
     * @param id The unique identifier of the GitHub repository configuration to retrieve.
     * @return An [Optional] containing the [GithubRepositoryConfig] if found, or an empty [Optional]
     *         if no configuration exists for the provided identifier.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving GitHub repository config")
    fun findConfigById(id: UUID): Optional<GithubRepositoryConfig> = configRepository.findById(id)

    /**
     * Saves the given GitHub repository configuration to the repository.
     *
     * @param config the GitHub repository configuration to be saved
     */
    @Tracked("Saving a GitHub repository config")
    fun saveRepositoryConfig(config: GithubRepositoryConfig) = configRepository.save(config)

    /**
     * Finds the configuration for a GitHub repository based on the repository owner's username
     * and the repository's name.
     *
     * @param owner The username of the repository owner.
     * @param name The name of the repository.
     * @return The configuration for the specified GitHub repository.
     * @throws RepositoryNotConnectedException (400) if the repository is not connected or does not exist.
     * @throws RepositoryConfigNotFoundException (404) if the configuration for the repository is not found.
     */
    private fun findConfigByRepositoryOwnerAndName(owner: String, name: String): GithubRepositoryConfig {
        val repository = githubRepoRepository.findByOwnerAndName(owner, name)
            ?: throw RepositoryNotConnectedException(owner, name)

        return configRepository.findById(repository.id).orElseThrow {
            RepositoryConfigNotFoundException(owner, name)
        }
    }
}
