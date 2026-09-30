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
     * A config with no next sync never matches the comparison, which keeps a freshly created
     * config out of a due sweep until its first sync is computed. Disabled repositories are
     * filtered out here so a paused source is never loaded by a sweep. Written as an explicit
     * query because the nested property cannot be named without an underscore, which the
     * function-naming rule rejects.
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
