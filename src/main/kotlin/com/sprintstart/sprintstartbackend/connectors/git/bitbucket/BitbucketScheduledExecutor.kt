package com.sprintstart.sprintstartbackend.connectors.git.bitbucket

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketRepositoryConfigService
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.service.BitbucketUpdatesService
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduledExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant

/**
 * Registers and regularly runs the Bitbucket connector's synchronization jobs.
 *
 * A tick reads the configurations whose next sync is due and updates exactly those, so a repository
 * is now updated on its own schedule instead of on every tick. Updating a repository is launched as
 * a background job, so a slow clone does not stall the tick or the repositories behind it.
 *
 * The next sync is advanced for every due config, including one whose auto-update is off. That keeps
 * a repository with auto-update off on its schedule rather than leaving it permanently due, so
 * turning auto-update back on resumes normally instead of firing once for every interval it sat out.
 *
 * A repository whose source is disabled never reaches this tick at all: the due query already
 * excludes it (see [BitbucketRepositoryConfigService.findConfigsDueForSync]). That is how
 * `sourceEnabled` pauses the scheduled ingest, and it mirrors the Confluence connector, which
 * filters disabled connections in the same place.
 */
@Component
internal class BitbucketScheduledExecutor(
    private val scheduledExecutor: ScheduledExecutor,
    private val configService: BitbucketRepositoryConfigService,
    private val updateService: BitbucketUpdatesService,
) {
    @Scheduled(fixedRate = 60_000)
    fun tick() {
        val now = Instant.now()
        val dueConfigs = configService.findConfigsDueForSync(now)

        dueConfigs.forEach { config ->
            if (config.autoUpdate) {
                scheduledExecutor.launch("Updating Bitbucket repository '${config.id}'") {
                    updateService.updateRepository(config.id!!)
                }
            }

            config.nextSyncAt = BitbucketRepositoryConfigService.calculateNextSyncAt(config.schedule)
            configService.saveRepositoryConfig(config)
        }
    }
}
