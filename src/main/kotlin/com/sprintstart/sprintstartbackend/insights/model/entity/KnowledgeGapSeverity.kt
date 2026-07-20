package com.sprintstart.sprintstartbackend.insights.model.entity

/**
 * Impact level of a knowledge gap, in descending severity order.
 *
 * The declaration order (HIGH first) doubles as the sort weight for the overview. [apiValue] and
 * [fromApiValue] translate between the enum and the lowercase representation used on the API and by
 * the AI service.
 */
enum class KnowledgeGapSeverity {
    HIGH,
    MEDIUM,
    LOW,
    ;

    val apiValue: String get() = name.lowercase()

    companion object {
        fun fromApiValue(value: String): KnowledgeGapSeverity =
            entries.firstOrNull { it.apiValue == value.lowercase() }
                ?: throw IllegalArgumentException("Unknown knowledge gap severity: $value")
    }
}
