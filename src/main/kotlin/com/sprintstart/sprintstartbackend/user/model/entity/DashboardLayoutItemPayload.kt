package com.sprintstart.sprintstartbackend.user.model.entity

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import kotlinx.serialization.Serializable

/**
 * How much of a dashboard arrangement the server will take.
 *
 * A dashboard holds around a dozen widgets; the numbers are far above anything a person arranges
 * by hand and low enough that one client cannot decide how large a stored row gets.
 */
object DashboardLayoutLimits {
    /** Placed widgets. The catalog has about a dozen. */
    const val ITEMS = 100

    /** A widget id or size name: short strings the client defines. */
    const val SHORT_TEXT = 100
}

/**
 * One placed widget: which one, and how large. Order in the list is the reading order on screen.
 *
 * Both fields are strings the client owns. The catalog of widgets and the size vocabulary live in
 * the frontend and change there; checking them here would mean every new widget needs a backend
 * release. The client already drops ids and sizes it does not know when it reads a layout back.
 */
@Serializable
data class DashboardLayoutItemPayload(
    @field:NotBlank
    @field:Size(max = DashboardLayoutLimits.SHORT_TEXT)
    val id: String,
    @field:NotBlank
    @field:Size(max = DashboardLayoutLimits.SHORT_TEXT)
    val size: String,
)
