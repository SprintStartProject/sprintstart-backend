package com.sprintstart.sprintstartbackend.connectors.github.models.exceptions

data class UserWithAuthIdNotFoundException(
    val authId: String,
) : RuntimeException("User with auth id $authId could not be found.")
