package com.sprintstart.sprintstartbackend.ingestion.model.exceptions

/**
 * Thrown if the AI repo returns an error to us.
 *
 * @property message The error message.
 * @property cause The error cause.
 */
internal class IngestionResponseException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
