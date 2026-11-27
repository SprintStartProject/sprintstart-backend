package com.sprintstart.sprintstartbackend.ingestion.external.model.dto

import java.time.Instant

/**
 * One saved version of a documentation page.
 *
 * @property repository The key of the space the page lives in, for example `confluence:ARCH`.
 * @property path The current title of the page.
 * @property authorAccountId The account ID of the person who saved the version at the source.
 * @property editedAt When the version was saved.
 */
data class PageEditReading(
    val repository: String,
    val path: String,
    val authorAccountId: String,
    val editedAt: Instant,
)
