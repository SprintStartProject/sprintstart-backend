package com.sprintstart.sprintstartbackend.shared.ids

import java.util.UUID

/** [value] as a [UUID], or null when it is not one, for ids that arrive as text from outside. */
fun parseUuidOrNull(value: String): UUID? =
    try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        null
    }
