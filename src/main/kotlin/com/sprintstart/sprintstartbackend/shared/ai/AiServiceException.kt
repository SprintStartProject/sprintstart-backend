package com.sprintstart.sprintstartbackend.shared.ai

/**
 * A call to the AI service failed.
 *
 * [statusCode] is an HTTP status: the service's own answer, or, for a stream that failed or ended
 * without a result, the one that describes it best. [body] is what the service said, if anything.
 */
class AiServiceException(
    val statusCode: Int,
    val body: String,
    message: String,
) : RuntimeException(message)
