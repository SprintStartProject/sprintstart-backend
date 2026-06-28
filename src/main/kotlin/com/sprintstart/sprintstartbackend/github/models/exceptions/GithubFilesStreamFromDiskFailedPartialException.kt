package com.sprintstart.sprintstartbackend.github.models.exceptions

data class GithubFilesStreamFromDiskFailedPartialException(
    val msg: String,
) : RuntimeException(msg)
