package com.sprintstart.sprintstartbackend.github.models.client.dto

import java.time.Instant

data class Commit(
    val date: Instant,
    val sha: String,
    val author: String,
    val msg: String,
)
