package com.sprintstart.sprintstartbackend.offboarding.scoring

import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Says in plain sentences why a file is on the risk map, so a reader does not have to decode the numbers.
 *
 * The sentences never name a person: the names are their own fields, so a reader that must not see them,
 * such as the pseudonymised export, can keep the sentences as they are.
 */
object Reasons {
    private const val ACTIVE_FROM = 0.5
    private const val HOTSPOT_FROM = 0.5
    private const val ABANDONED_FROM = 0.5
    private const val ORPHANED_FROM = 0.9
    private const val DAYS_PER_MONTH = 30
    private const val PERCENT = 100

    /** The sentences for [risk], with its age counted from [asOf]. */
    fun of(risk: FileRisk, asOf: Instant): List<String> =
        listOfNotNull(
            if (risk.level ==
                RiskLevel.ORPHANED
            ) {
                "Nobody who knows it is left."
            } else {
                "Only one person who knows it is left."
            },
            "${percent(risk.departingShare)}% of the evidence about it comes from the leaving people.",
            "One person holds almost all of the knowledge of it.".takeIf { risk.concentration.island != null },
            "It is among the more active files.".takeIf { risk.recentActivity >= ACTIVE_FROM },
            "It is a hotspot: complicated and changed often.".takeIf { (risk.hotspot ?: 0.0) >= HOTSPOT_FROM },
            abandoned(risk.abandonedShare),
            risk.coupling.partners
                .firstOrNull()
                ?.let { "It usually changes together with ${it.path}." },
            lastChanged(Duration.between(risk.lastChangedAt, asOf).toDays()),
        )

    private fun abandoned(share: Double?): String? =
        when {
            share == null || share < ABANDONED_FROM -> null
            share >= ORPHANED_FROM -> "Almost all of its lines were written by people who are leaving or have left."
            else -> "${percent(share)}% of its lines were written by people who are leaving or have left."
        }

    private fun percent(share: Double): Int = (share * PERCENT).roundToInt()

    private fun lastChanged(days: Long): String =
        when (val months = days / DAYS_PER_MONTH) {
            0L -> "It changed within the last month."
            1L -> "It last changed a month ago."
            else -> "It last changed $months months ago."
        }
}
