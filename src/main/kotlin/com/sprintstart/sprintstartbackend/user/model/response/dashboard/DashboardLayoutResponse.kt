package com.sprintstart.sprintstartbackend.user.model.response.dashboard

import com.sprintstart.sprintstartbackend.user.model.request.dashboard.DashboardLayoutItemPayload
import java.time.Instant

/**
 * How this user has arranged their dashboard.
 *
 * [updatedAt] is null when there is no arrangement to use — none stored, or one stored under a
 * different version — which means "show the default". An empty [items] with a non-null
 * [updatedAt] is a different fact: somebody who took every widget off on purpose.
 */
data class DashboardLayoutResponse(
    val version: Int,
    val items: List<DashboardLayoutItemPayload>,
    val updatedAt: Instant?,
)
