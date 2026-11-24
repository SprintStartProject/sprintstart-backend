package com.sprintstart.sprintstartbackend.offboarding.scoring

/** How the files at risk are put in order. */
enum class Ranking {
    /** Orphaned files first, then by the departing people's share of the file and how active it is. */
    DEPARTING_SHARE,

    /** By the [PlaceholderScore] of the team's metric selection, highest first. */
    PLACEHOLDER,
}
