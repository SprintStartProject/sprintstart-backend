package com.sprintstart.sprintstartbackend.github.external.events

import java.util.UUID

/**
 * Event triggered when the synchronization of multiple files starts.
 *
 * This event serves as a notification that the synchronization process for a batch
 * of files has been initiated. It includes a unique transaction identifier to
 * track the operation and a list of file paths that are being synchronized.
 *
 * @property transactionId A unique identifier for the transaction associated with this synchronization event.
 * @property paths A list of file paths that are included in the synchronization process.
 */
class FilesSyncStartedEvent(
    val transactionId: UUID,
    val paths: List<String>,
)
