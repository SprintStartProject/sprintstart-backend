package com.sprintstart.sprintstartbackend.github.external.events

import java.util.UUID

/**
 * Event triggered when the synchronization of multiple Git commits starts.
 *
 * This event is used to signal the initiation of a synchronization process
 * for a specified batch of commit identifiers (SHAs). It includes a unique
 * transaction identifier to track the operation and a list of commit SHAs
 * involved.
 *
 * @property transactionId A unique identifier for the transaction associated with this synchronization event.
 * @property shas A list of commit SHAs that are included in the synchronization process.
 */
class CommitsSyncStartedEvent(
    val transactionId: UUID,
    val shas: List<String>,
)
