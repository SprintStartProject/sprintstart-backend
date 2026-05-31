package com.sprintstart.sprintstartbackend.chat.models.exceptions

/**
 * Thrown if the AI repo returns an error to us.
 *
 * @property message The error message.
 * @property cause The error cause.
 */
internal class AiResponseException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
