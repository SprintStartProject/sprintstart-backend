package com.sprintstart.sprintstartbackend.ingestion.external

import java.time.Instant

/**
 * Exported ingestion-module API for other backend modules.
 *
 * Exposes read-only ingestion metadata about a component without leaking the ingestion module's
 * internal entities. Other modules should depend on this interface instead of querying the
 * ingestion repositories directly.
 */
interface ArtifactIngestionApi {
    /**
     * Returns when a component (`owner/repo`) was first ingested, or null when it has no ingested
     * artifacts.
     */
    fun getFirstIngestedAt(component: String): Instant?

    /**
     * Batch variant of [getFirstIngestedAt]. Only components with a known timestamp are present in
     * the returned map.
     */
    fun getFirstIngestedAt(components: Collection<String>): Map<String, Instant>
}
