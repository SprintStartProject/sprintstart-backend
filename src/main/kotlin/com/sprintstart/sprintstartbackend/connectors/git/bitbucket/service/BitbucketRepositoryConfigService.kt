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
import com.sprintstart.sprintstartbackend.user.external.UserApi
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
    private val userApi: UserApi,
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
     * Retrieves the configuration of every connected repository the caller may reach.
     *
     * @param authId The authenticated caller subject, deciding which repositories are theirs.
     * @return One response per reachable config; empty when no repository is connected.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all Bitbucket repository configs")
    fun getAll(authId: String): List<GetBitbucketRepositoryConfigResponse> =
        configRepository.findAll()
            .filter { config -> userApi.canAccessConnection(authId, config.repository.projectIds) }
            .map { GetBitbucketRepositoryConfigResponse.of(it) }

    /**
     * Applies the same update behavior to every configured repository the caller may reach.
     *
     * @param authId The authenticated caller subject, deciding which repositories are theirs.
     * @param request The schedule and auto-update flag to apply to all reachable configs.
     */
    @Transactional
    @Tracked("Configuring all Bitbucket repositories")
    fun configureAll(authId: String, request: ConfigureBitbucketRepositoryRequest) {
        val configs = configRepository.findAll()
            .filter { config -> userApi.canAccessConnection(authId, config.repository.projectIds) }

        configs.forEach { config -> applyConfig(config, request) }

        configRepository.saveAll(configs)
    }

    /**
     * Retrieves the configuration of one connected repository the caller may reach.
     *
     * A repository linked to none of the caller's projects answers the same 400 as one that was
     * never connected, so the two cannot be told apart from the outside.
     *
     * @param authId The authenticated caller subject, deciding whether the repository is theirs.
     * @param workspace The Bitbucket workspace the repository belongs to.
     * @param slug The repository slug.
     * @return The repository's current configuration.
     * @throws BitbucketRepositoryNotConnectedException if no connection matches the coordinates, or
     *         none the caller may reach.
     * @throws BitbucketRepositoryConfigNotFoundException if the connection has no config.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving config of Bitbucket repository")
    fun getConfigOfRepository(
        authId: String,
        workspace: String,
        slug: String,
    ): GetBitbucketRepositoryConfigResponse =
        GetBitbucketRepositoryConfigResponse.of(findConfigByCoordinates(authId, workspace, slug))

    /**
     * Configures the update behavior of one connected repository the caller may reach.
     *
     * A repository linked to none of the caller's projects answers the same 400 as one that was
     * never connected, so the two cannot be told apart from the outside.
     *
     * @param authId The authenticated caller subject, deciding whether the repository is theirs.
     * @param workspace The Bitbucket workspace the repository belongs to.
     * @param slug The repository slug.
     * @param request The schedule and auto-update flag to apply.
     * @throws BitbucketRepositoryNotConnectedException if no connection matches the coordinates, or
     *         none the caller may reach.
     * @throws BitbucketRepositoryConfigNotFoundException if the connection has no config.
     */
    @Transactional
    @Tracked("Configuring Bitbucket repository")
    fun configure(authId: String, workspace: String, slug: String, request: ConfigureBitbucketRepositoryRequest) {
        val config = findConfigByCoordinates(authId, workspace, slug)

        applyConfig(config, request)

        configRepository.save(config)
    }

    /**
     * Retrieves every config of an enabled repository whose next sync is at or before [now].
     *
     * This is the executor's work queue. A config is returned as-is rather than with its connection,
     * because the executor only needs the config's own columns — its id is the repository id.
     *
     * A disabled repository is excluded here, which is what makes `sourceEnabled` gate the scheduled
     * ingest: it is never handed to the executor, so it is neither updated nor put back on its
     * schedule. Re-enabling it therefore syncs once immediately before resuming its normal cadence.
     *
     * @param now The instant to compare each config's next sync against.
     * @return The due configs of enabled repositories, or an empty list when nothing is due.
     */
    @Transactional(readOnly = true)
    @Tracked("Retrieving all Bitbucket repositories due for sync now")
    fun findConfigsDueForSync(now: Instant): List<BitbucketRepositoryConfig> =
        configRepository.findEnabledConfigsDueForSync(now)

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
     * two are reported separately rather than collapsed into one 404. A connection linked to none
     * of the caller's projects answers as a missing one.
     */
    private fun findConfigByCoordinates(
        authId: String,
        workspace: String,
        slug: String,
    ): BitbucketRepositoryConfig {
        val connection = connectionRepository.findByWorkspaceAndSlug(workspace, slug)
            ?: throw BitbucketRepositoryNotConnectedException(workspace = workspace, slug = slug)
        if (!userApi.canAccessConnection(authId, connection.projectIds)) {
            throw BitbucketRepositoryNotConnectedException(workspace = workspace, slug = slug)
        }

        return configRepository.findById(connection.id).orElseThrow {
            BitbucketRepositoryConfigNotFoundException(workspace, slug)
        }
    }
}
