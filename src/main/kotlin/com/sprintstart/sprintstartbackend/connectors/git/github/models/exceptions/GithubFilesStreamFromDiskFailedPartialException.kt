package com.sprintstart.sprintstartbackend.connectors.git.github.models.exceptions

data class GithubFilesStreamFromDiskFailedPartialException(
    val msg: String,
) : RuntimeException(msg)
