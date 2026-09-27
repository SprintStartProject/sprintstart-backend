package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConfigNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.request.ConfigureBitbucketRepositoryRequest
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response.GetBitbucketRepositoryConfigResponse
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketConnectionRepository
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository.BitbucketRepositoryConfigRepository
import com.sprintstart.sprintstartbackend.shared.annotations.Tracked
import com.sprintstart.sprintstartbackend.shared.scheduler.CronBuilder
import org.springframework.scheduling.support.CronExpression
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZonedDateTime

/**
 * Owns the update configuration of connected Bitbucket repositories.
 *
 * A configuration is created once, when the repository is connected, and this service only changes
 * it afterwards. It is the only writer of the `next_sync_at` bound the scheduled executor reads, so
 * a schedule change takes effect on the next tick without the executor knowing a change happened.
 *
 * The cron [BitbucketRepositoryConfig.schedule] is always derived from the submitted
 * [ScheduleSpec] through [CronBuilder], never taken from the caller: that keeps the stored schedule
 * and the structured spec consistent, so what the API returns reflects what it accepted.
 */
@Service
internal class BitbucketRepositoryConfigService(
    private val configRepository: BitbucketRepositoryConfigRepository,
    private val connectionRepository: BitbucketConnectionRepository,
    private val cronBuilder: CronBuilder,
) {
    companion object {
        /**
         * Calculates the next synchronization time for a cron schedule.
         *
         * @param schedule A Spring six-field cron expression, as produced by [CronBuilder].
         * @return The next matching instant, or `null` when the schedule cannot be parsed or has no
         *         further occurrence (for example a date that has already passed).
         */
        fun calculateNextSyncAt(schedule: String): Instant? =
            runCatching {
                CronExpression.parse(schedule).next(ZonedDateTime.now())?.toInstant()
            }.getOrNull()
    }

    /**
     * Retrieves the configuration of every connected repository.
     *
     * @return One response per config; empty when no repository is connected.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all Bitbucket repository configs")
    fun getAll(): List<GetBitbucketRepositoryConfigResponse> =
        configRepository.findAll().map { GetBitbucketRepositoryConfigResponse.of(it) }

    /**
     * Applies the same update behavior to every configured repository.
     *
     * @param request The schedule and auto-update flag to apply to all configs.
     */
    @Tracked("Configuring all Bitbucket repositories")
    fun configureAll(request: ConfigureBitbucketRepositoryRequest) {
        val configs = configRepository.findAll()

        configs.forEach { config -> applyConfig(config, request) }

        configRepository.saveAll(configs)
    }

    /**
     * Retrieves the configuration of one connected repository.
     *
     * @param workspace The Bitbucket workspace the repository belongs to.
     * @param slug The repository slug.
     * @return The repository's current configuration.
     * @throws BitbucketRepositoryNotConnectedException if no connection matches the coordinates.
     * @throws BitbucketRepositoryConfigNotFoundException if the connection has no config.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving config of Bitbucket repository")
    fun getConfigOfRepository(workspace: String, slug: String): GetBitbucketRepositoryConfigResponse =
        GetBitbucketRepositoryConfigResponse.of(findConfigByCoordinates(workspace, slug))

    /**
     * Configures the update behavior of one connected repository.
     *
     * @param workspace The Bitbucket workspace the repository belongs to.
     * @param slug The repository slug.
     * @param request The schedule and auto-update flag to apply.
     * @throws BitbucketRepositoryNotConnectedException if no connection matches the coordinates.
     * @throws BitbucketRepositoryConfigNotFoundException if the connection has no config.
     */
    @Tracked("Configuring Bitbucket repository")
    fun configure(workspace: String, slug: String, request: ConfigureBitbucketRepositoryRequest) {
        val config = findConfigByCoordinates(workspace, slug)

        applyConfig(config, request)

        configRepository.save(config)
    }

    /**
     * Retrieves every config whose next sync is at or before [now].
     *
     * This is the executor's work queue. A config is returned as-is rather than with its connection,
     * because the executor only needs the config's own columns — its id is the repository id.
     *
     * @param now The instant to compare each config's next sync against.
     * @return The due configs, or an empty list when nothing is due.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all Bitbucket repositories due for sync now")
    fun findConfigsDueForSync(now: Instant): List<BitbucketRepositoryConfig> =
        configRepository.findAllByNextSyncAtIsLessThanEqual(now)

    /**
     * Persists a config, used by the executor after it advances [BitbucketRepositoryConfig.nextSyncAt].
     *
     * @param config The config to persist.
     */
    @Tracked("Saving a Bitbucket repository config")
    fun saveRepositoryConfig(config: BitbucketRepositoryConfig) = configRepository.save(config)

    /** Sets the schedule, spec and auto-update flag, then recomputes the next sync from the schedule. */
    private fun applyConfig(config: BitbucketRepositoryConfig, request: ConfigureBitbucketRepositoryRequest) {
        config.autoUpdate = request.autoUpdate
        config.spec = request.schedule
        config.schedule = cronBuilder.build(request.schedule)
        config.nextSyncAt = calculateNextSyncAt(config.schedule)
    }

    /**
     * Resolves the config of a repository by its coordinates.
     *
     * A missing connection is a different failure from a missing config — the first means the
     * repository was never connected, the second that a connection somehow lost its config — so the
     * two are reported separately rather than collapsed into one 404.
     */
    private fun findConfigByCoordinates(workspace: String, slug: String): BitbucketRepositoryConfig {
        val connection = connectionRepository.findByWorkspaceAndSlug(workspace, slug)
            ?: throw BitbucketRepositoryNotConnectedException(workspace = workspace, slug = slug)

        return configRepository.findById(connection.id).orElseThrow {
            BitbucketRepositoryConfigNotFoundException(workspace, slug)
        }
    }
}
