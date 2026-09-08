package com.sprintstart.sprintstartbackend.insights.model.entity

/**
 * Impact level of a knowledge gap, in descending severity order.
 *
 * The declaration order (HIGH first) doubles as the sort weight for the overview. [apiValue] and
 * [fromApiValue] translate between the enum and the lowercase representation used on the API and by
 * the AI service.
 *
 * [COVERED] is not a gap: it marks a component the scan found nothing missing on. Those are stored
 * and served like any other row so the panel is the project's full component roster — a component
 * in good shape is a finding of its own, and omitting it made it indistinguishable from one that
 * was never ingested. Last in the declaration order, so it sorts below every real gap.
 */
enum class KnowledgeGapSeverity {
    HIGH,
    MEDIUM,
    LOW,
    COVERED,
    ;

    val apiValue: String get() = name.lowercase()

    companion object {
        fun fromApiValue(value: String): KnowledgeGapSeverity =
            entries.firstOrNull { it.apiValue == value.lowercase() }
                ?: throw IllegalArgumentException("Unknown knowledge gap severity: $value")
    }
}
