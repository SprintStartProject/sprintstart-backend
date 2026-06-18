package com.sprintstart.sprintstartbackend.github.external.events

import java.util.UUID

/**
 * Event triggered when the synchronization of multiple pull requests starts.
 *
 * This event is used to indicate the initiation of a synchronization process
 * for a specified batch of pull request numbers. It includes a unique transaction
 * identifier to track the synchronization operation and a list of pull request
 * numbers involved.
 *
 * @property transactionId A unique identifier for the transaction associated with this synchronization event.
 * @property prNumbers A list of pull request numbers that are included in the synchronization process.
 */
class PullRequestsSyncStartedEvent(
    val transactionId: UUID,
    val prNumbers: List<Int>,
)
