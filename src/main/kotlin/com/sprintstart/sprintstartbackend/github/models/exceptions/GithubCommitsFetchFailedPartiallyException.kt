package com.sprintstart.sprintstartbackend.github.models.exceptions

class GithubCommitsFetchFailedPartiallyException(
    val msg: String,
) : RuntimeException(msg)
