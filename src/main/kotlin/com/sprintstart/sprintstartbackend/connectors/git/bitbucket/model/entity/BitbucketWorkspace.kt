package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Records that workspace-level metadata for a Bitbucket workspace has been fetched.
 *
 * The slug is the natural primary key and the row is written only after a successful fetch.
 * [fetchedAt] decides whether the next repository connect or update reuses the stored metadata or
 * fetches it again: members join and leave, so a fetch older than the service's staleness bound
 * refreshes instead of being trusted. A null timestamp — rows written before the column existed —
 * always refreshes.
 */
@Entity
@Table(name = "bitbucket_workspaces")
class BitbucketWorkspace(
    @Id
    @Column(name = "slug", nullable = false)
    var slug: String,
    var name: String? = null,
    @Column(name = "fetched_at")
    var fetchedAt: Instant? = null,
)
