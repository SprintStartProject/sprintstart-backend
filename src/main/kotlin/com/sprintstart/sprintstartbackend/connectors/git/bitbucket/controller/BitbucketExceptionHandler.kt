package com.sprintstart.sprintstartbackend.connectors.git.bitbucket.controller

import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketProjectAccessDeniedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConfigNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryConnectionNotFoundException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryDoesNotExistException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.git.bitbucket.model.exceptions.BitbucketRepositoryNotEnabledException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * Maps the Bitbucket connector's exceptions to HTTP responses.
 *
 * Only exceptions that a caller can cause are handled here: a repository that was never connected
 * is the caller's mistake and answers 400, while a connection that lost its configuration is a
 * server-side inconsistency and answers 404. A repository Bitbucket cannot find, or cannot show to
 * the given credential, is something the caller named and answers 404 as well.
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

    /**
     * Maps an update of a disabled repository to 400, mirroring how the Confluence connector
     * refuses ingestion of a disabled space.
     */
    @ExceptionHandler(BitbucketRepositoryNotEnabledException::class)
    fun handleRepositoryNotEnabled(ex: BitbucketRepositoryNotEnabledException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message))

    /**
     * Maps a denied project access to 403, mirroring the GitHub connector's handling.
     */
    @ExceptionHandler(BitbucketProjectAccessDeniedException::class)
    fun handleProjectAccessDenied(ex: BitbucketProjectAccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ex.message))

    /**
     * Maps a repository Bitbucket does not know, or does not show to the given credential, to 404.
     *
     * Bitbucket answers both cases the same way, so the two cannot be told apart and the message
     * names both. Without this mapping the exception surfaced as a bare 500.
     */
    @ExceptionHandler(BitbucketRepositoryDoesNotExistException::class)
    fun handleRepositoryDoesNotExist(ex: BitbucketRepositoryDoesNotExistException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message))

    /**
     * Maps an unusable connection id to 404. Unknown and invisible connections deliberately share
     * this answer, so the two cannot be told apart from the outside.
     */
    @ExceptionHandler(BitbucketRepositoryConnectionNotFoundException::class)
    fun handleConnectionNotFound(ex: BitbucketRepositoryConnectionNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message))
}

internal data class ErrorResponse(
    val message: String?,
)
