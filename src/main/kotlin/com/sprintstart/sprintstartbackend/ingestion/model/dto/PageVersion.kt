package com.sprintstart.sprintstartbackend.ingestion.model.dto

import java.time.Instant

/**
 * One saved version of a documentation page from any source, and who saved it.
 *
 * @property number The version number, 1 for the version that created the page.
 * @property authorId The ID of the person at the source, such as an Atlassian account or a Notion user.
 * @property editedAt When it was saved.
 */
data class PageVersion(
    val number: Int,
    val authorId: String,
    val editedAt: Instant,
)
