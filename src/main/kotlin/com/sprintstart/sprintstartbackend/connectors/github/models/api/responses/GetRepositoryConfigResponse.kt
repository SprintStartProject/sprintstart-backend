package com.sprintstart.sprintstartbackend.connectors.github.models.api.responses

import com.sprintstart.sprintstartbackend.connectors.github.models.GithubRepositoryConfig
import com.sprintstart.sprintstartbackend.connectors.github.models.ScheduleSpec
import java.time.Instant
import java.util.UUID

data class GetRepositoryConfigResponse(
    var id: UUID,
    var repositoryOwner: String,
    var repositoryName: String,
    var autoUpdate: Boolean,
    var spec: ScheduleSpec?,
    var schedule: String,
    var nextSyncAt: Instant?,
) {
    companion object {
        internal fun of(config: GithubRepositoryConfig): GetRepositoryConfigResponse {
            return GetRepositoryConfigResponse(
                id = config.id!!,
                repositoryOwner = config.repository.owner,
                repositoryName = config.repository.name,
                autoUpdate = config.autoUpdate,
                spec = config.spec,
                schedule = config.schedule,
                nextSyncAt = config.nextSyncAt,
            )
        }
    }
}
