package com.sprintstart.sprintstartbackend.connectors.github.controller

import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNameAlreadyExistsException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.GithubUserPatStillInUseException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotConnectedException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotFoundException
import com.sprintstart.sprintstartbackend.connectors.github.models.exceptions.RepositoryNotInitializedException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/**
 * Handles exceptions thrown during the operation of the application and maps them
 * to appropriate HTTP responses.
 *
 * This class is annotated with `@ControllerAdvice` to centralize exception handling
 * across the application. Each exception handler method catches specific exceptions
 * and returns a meaningful error response with an appropriate HTTP status code.
 */
@ControllerAdvice
class ExceptionHandler {
    /**
     * Handles exceptions of type [RepositoryNotFoundException] and converts them into
     * a standardized error response with a 404 NOT FOUND HTTP status code.
     *
     * @param ex The exception containing details about the repository that could not be found.
     * @return A [ResponseEntity] containing the [ErrorResponse] with the exception's message
     *         and an HTTP status of 404 (NOT FOUND).
     */
    @ExceptionHandler(RepositoryNotFoundException::class)
    fun handleRepositoryNotFound(ex: RepositoryNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type [RepositoryNotInitializedException] and maps them to
     * a standardized error response with a 400 BAD REQUEST HTTP status code.
     *
     * @param ex The exception containing details about the repository that is connected but not initialized.
     * @return A [ResponseEntity] containing the [ErrorResponse] with the exception's message
     *         and an HTTP status of 400 (BAD REQUEST).
     */
    @ExceptionHandler(RepositoryNotInitializedException::class)
    fun handleRepositorySnapshotNotFound(ex: RepositoryNotInitializedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type [RepositoryNotConnectedException] and converts them
     * into a standardized error response with a 400 BAD REQUEST HTTP status code.
     *
     * @param ex The exception containing details about the repository that is not connected.
     * @return A [ResponseEntity] containing the [ErrorResponse] with the exception's message
     *         and an HTTP status of 400 (BAD REQUEST).
     */
    @ExceptionHandler(RepositoryNotConnectedException::class)
    fun handleRepositorySnapshotNotFound(ex: RepositoryNotConnectedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type [GithubUserPatNotFoundException] and returns a standardized
     * error response with a 404 NOT FOUND HTTP status code.
     *
     * @param ex The exception containing details about the GitHub personal access token (PAT)
     *           that could not be found, including the name of the PAT and the associated user ID.
     * @return A [ResponseEntity] containing the [ErrorResponse] with the exception's message
     *         and an HTTP status of 404 (NOT FOUND).
     */
    @ExceptionHandler(GithubUserPatNotFoundException::class)
    fun handleGithubUserPatNotFound(ex: GithubUserPatNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type [GithubUserPatNameAlreadyExistsException] and converts them
     * into a standardized error response with a 400 BAD REQUEST HTTP status code.
     *
     * @param ex The exception containing details about the GitHub personal access token (PAT) name
     *           that already exists.
     * @return A [ResponseEntity] containing the [ErrorResponse] with the exception's message
     *         and an HTTP status of 400 (BAD REQUEST).
     */
    @ExceptionHandler(GithubUserPatNameAlreadyExistsException::class)
    fun handleGithubUserPatNameAlreadyExists(
        ex: GithubUserPatNameAlreadyExistsException,
    ): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message))

    /**
     * Handles exceptions of type [GithubUserPatStillInUseException] and converts them into
     * a standardized error response with a 400 BAD REQUEST HTTP status code.
     *
     * @param ex The exception containing details about the GitHub personal access token (PAT)
     *           that is still in use, including the name of the PAT.
     * @return A [ResponseEntity] containing the [ErrorResponse] with the exception's message
     *         and an HTTP status of 400 (BAD REQUEST).
     */
    @ExceptionHandler(GithubUserPatStillInUseException::class)
    fun handleGithubUserPatStillInUse(ex: GithubUserPatStillInUseException) =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message))
}

data class ErrorResponse(
    val message: String?,
)
