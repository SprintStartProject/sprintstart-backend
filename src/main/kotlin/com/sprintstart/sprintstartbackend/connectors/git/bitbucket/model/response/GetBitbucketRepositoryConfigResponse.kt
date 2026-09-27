package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.response

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketRepositoryConfig
import com.sprintstart.sprintstartbackend.shared.scheduler.ScheduleSpec
import java.time.Instant
import java.util.UUID

internal data class GetBitbucketRepositoryConfigResponse(
    val id: UUID,
    val workspace: String,
    val slug: String,
    val autoUpdate: Boolean,
    val spec: ScheduleSpec?,
    val schedule: String,
    val nextSyncAt: Instant?,
) {
    companion object {
        /** Maps a stored config to its API response, reading the coordinates from its connection. */
        internal fun of(config: BitbucketRepositoryConfig): GetBitbucketRepositoryConfigResponse =
            GetBitbucketRepositoryConfigResponse(
                id = config.id!!,
                workspace = config.repository.workspace,
                slug = config.repository.slug,
                autoUpdate = config.autoUpdate,
                spec = config.spec,
                schedule = config.schedule,
                nextSyncAt = config.nextSyncAt,
            )
    }
}
