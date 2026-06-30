package com.sprintstart.sprintstartbackend.connectors.github.models.exceptions

class GithubCommitsFetchFailedPartiallyException(
    val msg: String,
) : RuntimeException(msg)
