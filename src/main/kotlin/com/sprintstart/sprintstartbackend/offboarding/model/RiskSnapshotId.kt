package com.sprintstart.sprintstartbackend.offboarding.model

import java.util.UUID

/** A risk map that was computed and stored, so it can be read again and compared. */
@JvmInline
value class RiskSnapshotId(
    val value: UUID,
)
