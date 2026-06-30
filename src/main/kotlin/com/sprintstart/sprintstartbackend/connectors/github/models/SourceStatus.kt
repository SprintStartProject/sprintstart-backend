package com.sprintstart.sprintstartbackend.connectors.github.models

enum class SourceStatus {
    INCLUDED,
    EXCLUDED,
    UNKNOWN, // Fallback, just in case
}

/**
 * Parses a value of this enum into it's clean string form.
 *
 * @return the enum value's string representation
 */
fun SourceStatus.parse(): String = when (this) {
    SourceStatus.INCLUDED -> "included"
    SourceStatus.EXCLUDED -> "excluded"
    SourceStatus.UNKNOWN -> "unknown"
}
