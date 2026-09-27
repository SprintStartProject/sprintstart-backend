package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.controller

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConfigNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * Maps the Bitbucket connector's exceptions to HTTP responses.
 *
 * Only exceptions that a caller can cause are handled here: a repository that was never connected
 * is the caller's mistake and answers 400, while a connection that lost its configuration is a
 * server-side inconsistency and answers 404.
 */
@ControllerAdvice
internal class BitbucketExceptionHandler {
    /**
     * Maps a missing config to 404, since the connection exists but its config does not.
     */
    @ExceptionHandler(BitbucketRepositoryConfigNotFoundException::class)
    fun handleRepositoryConfigNotFound(ex: BitbucketRepositoryConfigNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message))

    /**
     * Maps an unconnected repository to 400, since the caller addressed coordinates the application
     * does not know about.
     */
    @ExceptionHandler(BitbucketRepositoryNotConnectedException::class)
    fun handleRepositoryNotConnected(ex: BitbucketRepositoryNotConnectedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message))
}

internal data class ErrorResponse(
    val message: String?,
)
