package com.sprintstart.sprintstartbackend.connectors.github.models.client.dto

import java.time.Instant

/**
 * Represents a single commit in a GitHub repository in a simple form.
 */
data class Commit(
    val date: Instant,
    val sha: String,
    val author: String,
    val msg: String,
)
