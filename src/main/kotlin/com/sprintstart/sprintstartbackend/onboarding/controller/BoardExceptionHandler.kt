package com.sprintstart.sprintstartbackend.onboarding.controller

import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Turns a lost race on a board card into the answer the client can act on.
 *
 * Cards are version-checked (see `BoardCard.version`), so when two requests change the same card
 * at once the second one's write fails rather than committing over the first. That is a conflict
 * the client resolves by reloading the card, not a server fault — hence 409 and not 500.
 */
@RestControllerAdvice(assignableTypes = [BoardController::class])
class BoardExceptionHandler {
    /** A card changed between this request reading it and writing it; nothing was written. */
    @ExceptionHandler(OptimisticLockingFailureException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleCardChangedConcurrently(): ProblemDetail =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT,
            "That card changed while you were working on it — reload it and try again",
        )
}
