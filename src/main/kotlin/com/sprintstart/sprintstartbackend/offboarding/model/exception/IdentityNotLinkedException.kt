package com.sprintstart.sprintstartbackend.offboarding.model.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** The identity a manager wants to unlink is not linked to anyone in the project. */
@ResponseStatus(HttpStatus.NOT_FOUND, reason = "That identity is not linked")
class IdentityNotLinkedException(
    alias: String,
) : RuntimeException("$alias is not linked to anyone")
