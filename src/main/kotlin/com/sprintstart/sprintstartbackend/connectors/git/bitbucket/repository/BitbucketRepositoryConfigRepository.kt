package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.repository

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity.BitbucketRepositoryConfig
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
internal interface BitbucketRepositoryConfigRepository : JpaRepository<BitbucketRepositoryConfig, UUID> {
    /**
     * Returns the configs whose next sync is at or before [due].
     *
     * A config with no [BitbucketRepositoryConfig.nextSyncAt] is never returned: a null bound cannot
     * satisfy the comparison, which is what keeps a freshly created, not-yet-scheduled config out of
     * a due sweep until its first next-sync is computed.
     */
    fun findAllByNextSyncAtIsLessThanEqual(due: Instant): List<BitbucketRepositoryConfig>
}
