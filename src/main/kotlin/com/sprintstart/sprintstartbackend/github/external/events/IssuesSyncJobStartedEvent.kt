package com.sprintstart.sprintstartbackend.github.external.events

import java.util.UUID

/**
 * Event triggered when the synchronization of multiple GitHub issues starts.
 *
 * This event denotes the beginning of a synchronization process for a batch of
 * GitHub issues. It is used to signal the commencement of retrieving or updating
 * the issues associated with a repository. The event includes a unique transaction
 * identifier for tracking purposes and a list of issue numbers that are being
 * synchronized.
 *
 * @property transactionId A unique identifier for the transaction associated with this synchronization event.
 * @property issueNumbers A list of issue numbers included in the synchronization process.
 */
class IssuesSyncJobStartedEvent(
    val transactionId: UUID,
    val issueNumbers: List<Int>,
)
