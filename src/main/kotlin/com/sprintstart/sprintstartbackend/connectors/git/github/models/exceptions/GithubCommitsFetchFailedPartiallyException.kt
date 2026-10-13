package com.sprintstart.sprintstartbackend.connectors.git.github.models.exceptions

class GithubCommitsFetchFailedPartiallyException(
    val msg: String,
) : RuntimeException(msg)
