package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketRepositoryConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
internal interface BitbucketRepositoryConfigRepository : JpaRepository<BitbucketRepositoryConfig, UUID> {
    /**
     * Returns the configs of **enabled** repositories whose next sync is at or before [due].
     *
     * A config with no [BitbucketRepositoryConfig.nextSyncAt] is never returned: a null bound cannot
     * satisfy the comparison, which is what keeps a freshly created, not-yet-scheduled config out of
     * a due sweep until its first next-sync is computed.
     *
     * Disabled repositories are filtered out here rather than skipped by the executor, so a paused
     * source is never even loaded by a due sweep. This is the same place the Confluence connector
     * excludes disabled connections, which keeps the scheduled behaviour of the two connectors
     * aligned.
     *
     * Written as an explicit query rather than a derived one because the nested property cannot be
     * named without an underscore, and the project's function-naming rule rejects underscores. An
     * explicit query keeps detekt and Spring Data from disagreeing about the same method.
     *
     * @param due The instant to compare each config's next sync against.
     * @return The due configs of enabled repositories, or an empty list when nothing is due.
     */
    @Query(
        """
            SELECT c FROM BitbucketRepositoryConfig c
            WHERE c.nextSyncAt <= :due AND c.repository.sourceEnabled = true
        """,
    )
    fun findEnabledConfigsDueForSync(@Param("due") due: Instant): List<BitbucketRepositoryConfig>
}
