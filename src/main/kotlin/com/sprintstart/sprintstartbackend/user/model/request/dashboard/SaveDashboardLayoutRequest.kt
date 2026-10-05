package com.sprintstart.sprintstartbackend.user.model.request.dashboard

import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

/**
 * The whole dashboard arrangement, replacing whatever was stored.
 *
 * Sent whole rather than as a patch: the arrangement is small and the client holds all of it.
 * [version] is the client's layout version (`LAYOUT_VERSION` in the frontend) the items were
 * written under.
 */
data class SaveDashboardLayoutRequest(
    @field:Positive
    val version: Int,
    @field:Size(max = DashboardLayoutLimits.ITEMS)
    @field:Valid
    val items: List<DashboardLayoutItemPayload>,
)
