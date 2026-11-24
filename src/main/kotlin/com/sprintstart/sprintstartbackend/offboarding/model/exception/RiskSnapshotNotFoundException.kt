package com.sprintstart.sprintstartbackend.offboarding.model.exception

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

/** The stored risk map does not exist, or belongs to another project. */
@ResponseStatus(HttpStatus.NOT_FOUND, reason = "No such risk map in this project")
class RiskSnapshotNotFoundException(
    snapshot: String,
) : RuntimeException("No risk map $snapshot in this project")
