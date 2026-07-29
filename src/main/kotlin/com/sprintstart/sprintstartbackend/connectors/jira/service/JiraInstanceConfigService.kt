package com.sprintstart.sprintstartbackend.connectors.jira.service

import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.config.ConfigureAllJiraInstancesRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.request.config.ConfigureJiraInstanceRequest
import com.sprintstart.sprintstartbackend.connectors.jira.model.api.response.config.GetJiraInstanceConfigResponse
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstance
import com.sprintstart.sprintstartbackend.connectors.jira.model.entity.JiraInstanceConfig
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceConfigNotFoundException
import com.sprintstart.sprintstartbackend.connectors.jira.model.exceptions.JiraInstanceNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.jira.repository.JiraInstanceConfigRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.scheduler.CronBuilder
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZonedDateTime

@Service
internal class JiraInstanceConfigService(
    private val configRepository: JiraInstanceConfigRepository,
    private val cronBuilder: CronBuilder,
) {
    /**
     * Calculates the next synchronization time based on the provided cron schedule.
     *
     * @param schedule A string representing the cron expression that defines the schedule of synchronization.
     * @return The next synchronization time as an [Instant], or null if the calculation fails.
     */
    @Tracked("Calculating next sync time")
    fun calculateNextSyncAt(schedule: String): Instant? =
        runCatching {
            val cron = CronExpression.parse(schedule)
            cron.next(ZonedDateTime.now())?.toInstant()
        }.getOrNull()

    /**
     * Retrieves all Jira instance configurations.
     *
     * @return A list of [GetJiraInstanceConfigResponse] representing the configurations of all Jira instances.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all Jira instance configs")
    fun getAll(): List<GetJiraInstanceConfigResponse> =
        configRepository.findAll().map { GetJiraInstanceConfigResponse.of(it) }

    /**
     * Configures all Jira instance configurations based on the provided request.
     * Updates each Jira instance configuration's auto-update setting, schedule specification,
     * computed schedule, and calculates the next synchronization time.
     *
     * @param request An instance of [ConfigureAllJiraInstancesRequest] containing the schedule specification
     * and auto-update flag to be applied to all Jira instance configurations.
     */
    @Tracked("Configuring all Jira instance configs")
    fun configureAll(request: ConfigureAllJiraInstancesRequest) {
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
     * Retrieves the configuration details of a specific Jira instance.
     *
     * @param instanceUrl The URL of the Jira instance for which the configuration is being retrieved.
     * @return An instance of [GetJiraInstanceConfigResponse] containing the configuration details of the specified Jira
     *         instance.
     * @throws JiraInstanceNotConnectedException If no configuration is found for the given Jira instance URL.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving config of Jira instance")
    fun getConfigOfInstance(instanceUrl: String): GetJiraInstanceConfigResponse {
        val config = configRepository.findById(instanceUrl).orElseThrow {
            JiraInstanceNotConnectedException(instanceUrl)
        }
        return GetJiraInstanceConfigResponse.of(config)
    }

    /**
     * Configures a specific Jira instance by updating its configuration details
     * such as auto-update setting, schedule specification, computed schedule,
     * and calculates the next synchronization time.
     *
     * @param request An instance of [ConfigureJiraInstanceRequest] containing the
     * configuration details such as the instance URL, auto-update flag, and
     * schedule specification necessary for configuring the Jira instance.
     * @throws JiraInstanceNotConnectedException If no configuration is found for
     * the given Jira instance URL.
     */
    @Tracked("Configuring Jira instance")
    fun configureInstance(request: ConfigureJiraInstanceRequest) {
        val config = configRepository.findById(request.instanceUrl).orElseThrow {
            JiraInstanceNotConnectedException(request.instanceUrl)
        }

        config.autoUpdate = request.autoUpdate
        config.spec = request.schedule
        config.schedule = cronBuilder.build(request.schedule)
        config.nextSyncAt = calculateNextSyncAt(config.schedule)

        configRepository.save(config)
    }

    /**
     * Retrieves a list of Jira instances that are due for synchronization at the specified time.
     *
     * @param now An instance of [Instant] representing the current time to determine which Jira instances are due for
     *            sync.
     * @return A list of [JiraInstance] that are scheduled to be synchronized at the specified time.
     * @throws JiraInstanceConfigNotFoundException if any configuration referenced during lookup is not found.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all Jira instances due for sync now")
    fun findAllJiraInstanceConfigsDueForSync(now: Instant): List<JiraInstanceConfig> {
        return configRepository.findAllByNextSyncAtIsLessThanEqual(now)
    }

    /**
     * Persists the provided Jira instance configuration into the repository.
     *
     * @param config The [JiraInstanceConfig] object representing the configuration details
     * of a Jira instance to be saved.
     */
    @Tracked("Saving a Jira instance config")
    fun saveJiraInstanceConfig(config: JiraInstanceConfig) = configRepository.save(config)
}
